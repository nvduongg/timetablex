import React, { useEffect, useState } from 'react';
import {
  Table, Button, Tag, Switch, message, Modal, Typography, Space, Card, Flex, Select, Form
} from 'antd';
import { UserAddOutlined, ReloadOutlined, KeyOutlined } from '@ant-design/icons';
import * as AuthService from '../services/authService';
import * as FacultyService from '../services/facultyService';

const { Text } = Typography;
const { Option } = Select;

const UserManagement = () => {
  const [users, setUsers] = useState([]);
  const [faculties, setFaculties] = useState([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm] = Form.useForm();

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const res = await AuthService.getUsers();
      setUsers(res.data || res);
    } catch {
      message.error('Lỗi tải danh sách tài khoản');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
    FacultyService.getFaculties().then(res => setFaculties(res.data || res));
  }, []);

  const handleToggleActive = async (user) => {
    try {
      await AuthService.toggleUserActive(user.id);
      message.success(user.active ? 'Đã khóa tài khoản' : 'Đã mở khóa tài khoản');
      fetchUsers();
    } catch {
      message.error('Lỗi thay đổi trạng thái tài khoản');
    }
  };

  const handleResetPassword = async (user) => {
    Modal.confirm({
      title: 'Reset mật khẩu?',
      content: `Reset mật khẩu cho tài khoản "${user.username}"? Mật khẩu mới sẽ được tạo ngẫu nhiên.`,
      okText: 'Reset',
      okType: 'primary',
      cancelText: 'Hủy',
      async onOk() {
        try {
          const res = await AuthService.resetUserPassword(user.id);
          const data = res.data || res;
          Modal.success({
            title: 'Thông tin tài khoản',
            width: 420,
            content: (
              <div style={{ marginTop: 8 }}>
                <div style={{ marginBottom: 12 }}>
                  <Text type="secondary">Tên đăng nhập</Text>
                  <div><Text code copyable>{data.username}</Text></div>
                </div>
                <div style={{ marginBottom: 8 }}>
                  <Text type="secondary">Mật khẩu mới</Text>
                  <div><Text code copyable>{data.newPassword}</Text></div>
                </div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Sao chép và gửi cho Khoa/Viện. Mật khẩu chỉ hiển thị một lần.
                </Text>
              </div>
            ),
          });
        } catch {
          message.error('Lỗi reset mật khẩu');
        }
      },
    });
  };

  const handleOpenCreateModal = () => {
    if (!faculties.length) {
      message.warning('Chưa có dữ liệu Khoa/Viện');
      return;
    }
    createForm.setFieldsValue({ facultyId: faculties[0].id });
    setCreateModalOpen(true);
  };

  const handleCreateFacultyAccount = async (values) => {
    try {
      const res = await AuthService.createFacultyAccount(values.facultyId);
      const data = res.data || res;
      message.success('Đã tạo tài khoản Khoa/Viện');
      setCreateModalOpen(false);
      createForm.resetFields();
      Modal.success({
        title: 'Thông tin tài khoản mới',
        width: 420,
        content: (
          <div style={{ marginTop: 8 }}>
            <div style={{ marginBottom: 12 }}>
              <Text type="secondary">Tên đăng nhập</Text>
              <div><Text code copyable>{data.username}</Text></div>
            </div>
            <div>
              <Text type="secondary">Mật khẩu</Text>
              <div><Text code copyable>{data.password}</Text></div>
            </div>
          </div>
        ),
      });
      fetchUsers();
    } catch {
      message.error('Lỗi tạo tài khoản Khoa/Viện');
    }
  };

  const columns = [
    {
      title: 'Tên đăng nhập',
      dataIndex: 'username',
      key: 'username',
      render: (t) => <Text strong style={{ color: '#005a8d' }}>{t}</Text>,
    },
    {
      title: 'Vai trò',
      dataIndex: 'role',
      key: 'role',
      width: 120,
      render: (r) => (
        <Tag color={r === 'ADMIN' ? 'blue' : 'green'} style={{ border: 'none' }}>
          {r === 'ADMIN' ? 'P.ĐT' : 'Khoa/Viện'}
        </Tag>
      ),
    },
    {
      title: 'Thuộc Khoa/Viện',
      dataIndex: 'facultyName',
      key: 'facultyName',
      render: (t) =>
        t ? (
          <Tag style={{ border: 'none', background: '#f0f7ff', color: '#005a8d' }}>{t}</Tag>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'Kích hoạt',
      dataIndex: 'active',
      key: 'active',
      width: 90,
      render: (_, record) => (
        <Switch
          checked={record.active}
          onChange={() => handleToggleActive(record)}
          size="small"
        />
      ),
    },
    {
      title: 'Hành động',
      key: 'action',
      width: 120,
      align: 'right',
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          icon={<KeyOutlined />}
          onClick={() => handleResetPassword(record)}
        >
          Reset mật khẩu
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 0, width: '100%', maxWidth: '100%', overflow: 'hidden', boxSizing: 'border-box' }}>
      <Card variant="borderless" styles={{ body: { padding: '20px 24px' } }}>
        <Flex wrap="wrap" gap={16} align="center" justify="space-between" style={{ marginBottom: 20 }}>
          <Text type="secondary" style={{ fontSize: 13 }}>
            Quản lý tài khoản đăng nhập cho Phòng Đào tạo và các Khoa/Viện.
          </Text>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchUsers}>
              Tải lại
            </Button>
            <Button
              type="primary"
              icon={<UserAddOutlined />}
              onClick={handleOpenCreateModal}
              disabled={!faculties.length}
            >
              Tạo tài khoản Khoa/Viện
            </Button>
          </Space>
        </Flex>

        <Table
          dataSource={users}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10, placement: 'bottomRight', showSizeChanger: false }}
        />
      </Card>

      <Modal
        title="Tạo tài khoản cho Khoa/Viện"
        open={createModalOpen}
        onCancel={() => { setCreateModalOpen(false); createForm.resetFields(); }}
        footer={null}
        width={420}
        centered
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={handleCreateFacultyAccount}
          style={{ marginTop: 20 }}
        >
          <Form.Item
            name="facultyId"
            label="Chọn Khoa/Viện"
            rules={[{ required: true, message: 'Chọn Khoa/Viện' }]}
          >
            <Select placeholder="Chọn Khoa/Viện" variant="filled" size="large">
              {faculties.map((f) => (
                <Option key={f.id} value={f.id}>
                  {f.name} ({f.code})
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item style={{ textAlign: 'right', marginBottom: 0, marginTop: 24 }}>
            <Space>
              <Button onClick={() => { setCreateModalOpen(false); createForm.resetFields(); }}>
                Hủy
              </Button>
              <Button type="primary" htmlType="submit">
                Tạo tài khoản
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagement;
