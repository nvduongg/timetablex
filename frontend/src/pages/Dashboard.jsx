import React from 'react';
import { Row, Col, Card, Typography, Button, Tag } from 'antd';
import { BookOutlined, ScheduleOutlined, TeamOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;

const Dashboard = ({ auth }) => {
  const displayName = auth?.role === 'FACULTY'
    ? (auth?.facultyName || 'Khoa/Viện')
    : 'Phòng Đào tạo';

  return (
    <div style={{ paddingTop: 8 }}>

      <Row gutter={16}>
        <Col xs={24} md={8}>
          <Card
            variant="borderless"
            style={{ borderRadius: 10, height: '100%' }}
            styles={{ body: { padding: 18 } }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <div style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                background: '#e6f4ff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#005a8d',
              }}>
                <ScheduleOutlined />
              </div>
              <div>
                <div style={{ fontWeight: 600 }}>Kế hoạch mở lớp</div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Tạo / import danh sách học phần dự kiến cho từng học kỳ.
                </Text>
              </div>
            </div>
            <Button
              type="primary"
              size="small"
              style={{ marginTop: 8 }}
              onClick={() => {
                window.scrollTo({ top: 0, behavior: 'smooth' });
              }}
            >
              Mở màn Quản lý Kế hoạch
            </Button>
          </Card>
        </Col>

        <Col xs={24} md={8}>
          <Card
            variant="borderless"
            style={{ borderRadius: 10, height: '100%' }}
            styles={{ body: { padding: 18 } }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <div style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                background: '#fff7e6',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#fa8c16',
              }}>
                <BookOutlined />
              </div>
              <div>
                <div style={{ fontWeight: 600 }}>Khung CTĐT & Môn học</div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Quản lý CTĐT, môn học và lộ trình học kỳ cho từng khóa.
                </Text>
              </div>
            </div>
            <Tag color="gold" style={{ border: 'none', marginTop: 8 }}>
              Tip: Thiết lập CTĐT trước khi chạy gợi ý kế hoạch.
            </Tag>
          </Card>
        </Col>

        <Col xs={24} md={8}>
          <Card
            variant="borderless"
            style={{ borderRadius: 10, height: '100%' }}
            styles={{ body: { padding: 18 } }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <div style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                background: '#f6ffed',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#52c41a',
              }}>
                <TeamOutlined />
              </div>
              <div>
                <div style={{ fontWeight: 600 }}>Tài khoản & Phân quyền</div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  (Dành cho P.ĐT) Sinh tài khoản cho các Khoa/Viện, quản lý quyền truy cập.
                </Text>
              </div>
            </div>
            <Tag color="green" style={{ border: 'none', marginTop: 8 }}>
              Sắp triển khai giao diện quản lý tài khoản.
            </Tag>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;

