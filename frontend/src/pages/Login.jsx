import React, { useState } from 'react';
import { Form, Input, Button, Typography, message } from 'antd';
import { login } from '../services/authService';
import campusImg from '../assets/rectangle-65-2.png';

const { Title, Text } = Typography;

const Login = ({ onLogin }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleFinish = async (values) => {
    setLoading(true);
    try {
      const res = await login(values.username, values.password);
      const data = res.data || res;
      localStorage.setItem('auth_token', data.token);
      localStorage.setItem('auth_user', JSON.stringify({
        username: data.username,
        role: data.role,
        facultyId: data.facultyId,
        facultyName: data.facultyName
      }));
      if (onLogin) onLogin(data);
      message.success('Đăng nhập thành công');
    } catch (e) {
      message.error(e?.response?.data?.message || 'Đăng nhập thất bại');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        height: '100vh',
        width: '100vw',
        display: 'flex',
        overflow: 'hidden',
        fontFamily: "'Poppins', sans-serif",
        backgroundColor: '#ffffff',
      }}
    >
      {/* Định nghĩa các hiệu ứng CSS Animation */}
      <style>
        {`
          @keyframes fadeInUp {
            0% { opacity: 0; transform: translateY(20px); }
            100% { opacity: 1; transform: translateY(0); }
          }
          @keyframes bgZoom {
            0% { transform: scale(1); }
            100% { transform: scale(1.08); }
          }
          .animate-fade-up {
            animation: fadeInUp 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
            opacity: 0;
          }
          .delay-1 { animation-delay: 0.1s; }
          .delay-2 { animation-delay: 0.2s; }
          .delay-3 { animation-delay: 0.3s; }
          .delay-4 { animation-delay: 0.4s; }
          .delay-5 { animation-delay: 0.5s; }
          /* Tăng khoảng cách giữa icon loading và text trong button */
          .ant-btn-loading .ant-btn-loading-icon {
            margin-right: 8px !important;
          }
          /* Áp dụng font Poppins cho các thông báo validation */
          .ant-form-item-explain-error,
          .ant-form-item-explain-success,
          .ant-form-item-explain-warning {
            font-family: 'Poppins', sans-serif !important;
          }
        `}
      </style>

      {/* CỘT TRÁI - BACKGROUND & TEXT */}
      <div
        style={{
          flex: 1.2,
          position: 'relative',
          overflow: 'hidden', // Quan trọng để ảnh nền zoom không bị tràn
        }}
      >
        {/* Ảnh nền với hiệu ứng zoom chậm */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            backgroundImage: `url(${campusImg})`,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
            animation: 'bgZoom 20s alternate infinite ease-in-out', // Hiệu ứng zoom
          }}
        />

        {/* Lớp phủ gradient làm tối ảnh nền */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background: 'linear-gradient(135deg, rgba(0, 21, 41, 0.9) 0%, rgba(0, 58, 110, 0.5) 100%)',
          }}
        />

        {/* Nội dung cột trái */}
        <div
          style={{
            position: 'relative',
            height: '100%',
            padding: '60px 80px',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            color: '#ffffff',
            zIndex: 1,
          }}
        >
          <div className="animate-fade-up delay-1">
            <div style={{ fontSize: 34, fontWeight: 800, letterSpacing: -0.8 }}>
              Timetable<span style={{ color: '#ffd666' }}>X</span>
            </div>
            <div style={{ marginTop: 8, textTransform: 'uppercase', fontSize: 12, letterSpacing: 3, opacity: 0.9 }}>
              Phenikaa University
            </div>
          </div>

          <div className="animate-fade-up delay-2">
            <Title level={2} style={{ color: '#ffffff', margin: '0 0 12px 0', fontWeight: 700 }}>
              Hệ thống hỗ trợ<br />xếp Thời khóa biểu
            </Title>
            <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 15, lineHeight: 1.6, display: 'block', maxWidth: 400 }}>
              Tối ưu quy trình lập kế hoạch mở lớp, duyệt Khoa/Viện và xếp TKB tự động một cách thông minh.
            </Text>
          </div>
        </div>

        {/* HIỆU ỨNG SÓNG BIỂN MÀU TRẮNG */}
        <div
          style={{
            position: 'absolute',
            right: -2,
            top: 0,
            height: '100%',
            width: '120px',
            zIndex: 2,
            pointerEvents: 'none',
          }}
        >
          <svg
            preserveAspectRatio="none"
            viewBox="0 0 100 1000"
            style={{ width: '100%', height: '100%' }}
          >
            <path d="M100,0 L100,1000 L20,1000 C80,750 0,500 60,250 C80,100 20,0 20,0 Z" fill="#ffffff" />
          </svg>
        </div>
      </div>

      {/* CỘT PHẢI - FORM ĐĂNG NHẬP (Bị thu hẹp lại bằng flex 0.6) */}
      <div
        style={{
          flex: 0.7,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#ffffff',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <div style={{ width: 380, maxWidth: '85%', padding: '20px 0' }}>
          <div style={{ marginBottom: 32 }} className="animate-fade-up delay-3">
            <Title level={3} style={{ marginBottom: 8, color: '#001529', fontWeight: 700 }}>
              Chào mừng trở lại!
            </Title>
            <Text type="secondary" style={{ fontSize: 14 }}>
              Sử dụng tài khoản P.ĐT hoặc Khoa/Viện đã được cấp để tiếp tục.
            </Text>
          </div>

          <Form form={form} layout="vertical" onFinish={handleFinish} size="large">
            {/* Đã xóa icon UserOutlined */}
            <Form.Item
              className="animate-fade-up delay-4"
              label={<span style={{ fontWeight: 500 }}>Tên đăng nhập</span>}
              name="username"
              rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập!' }]}
            >
              <Input 
                placeholder="Nhập tên đăng nhập" 
                autoComplete="username" 
                style={{ borderRadius: '8px' }}
              />
            </Form.Item>

            {/* Đã xóa icon LockOutlined */}
            <Form.Item
              className="animate-fade-up delay-4"
              label={<span style={{ fontWeight: 500 }}>Mật khẩu</span>}
              name="password"
              rules={[{ required: true, message: 'Vui lòng nhập mật khẩu!' }]}
            >
              <Input.Password 
                placeholder="Nhập mật khẩu" 
                autoComplete="current-password" 
                style={{ borderRadius: '8px' }}
              />
            </Form.Item>

            <Form.Item style={{ marginTop: 32, marginBottom: 0 }} className="animate-fade-up delay-5">
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                style={{ 
                  height: '46px', 
                  borderRadius: '8px', 
                  fontSize: '15px', 
                  fontWeight: 500,
                  background: 'linear-gradient(90deg, #001529 0%, #003a6e 100%)',
                  border: 'none',
                  transition: 'all 0.3s ease'
                }}
              >
                Đăng nhập hệ thống
              </Button>
            </Form.Item>
          </Form>
        </div>
      </div>
    </div>
  );
};

export default Login;