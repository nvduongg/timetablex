import React from 'react';
import { Layout, Menu, Dropdown } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';

const { Header, Content, Sider } = Layout;

const ALL_ITEMS = [
  { key: '/dashboard',           label: 'Tổng quan' },
  { key: '/faculties',           label: 'Quản lý Khoa/Viện' },
  { key: '/majors',              label: 'Quản lý Ngành' },
  { key: '/classes',             label: 'Quản lý Lớp' },
  { key: '/rooms',               label: 'Quản lý Phòng học' },
  { key: '/timeslots',           label: 'Quản lý Ca học' },
  { key: '/courses',             label: 'Quản lý Môn học' },
  { key: '/curriculum',          label: 'Quản lý CTĐT' },
  { key: '/lecturers',           label: 'Quản lý Giảng viên' },
  { key: '/semesters',           label: 'Quản lý Học kỳ' },
  { key: '/course-offerings',    label: 'Quản lý Kế hoạch mở lớp' },
  { key: '/faculty-approval',    label: 'Duyệt kế hoạch (Khoa/Viện)' },
  { key: '/class-sections',      label: 'Quản lý Lớp học phần' },
  { key: '/teaching-assignments',label: 'Phân công giảng dạy' },
  { key: '/support-requests',    label: 'Yêu cầu hỗ trợ GV' },
  { key: '/timetable',           label: 'Xếp Thời khóa biểu' },
  { key: '/users',               label: 'Quản lý Tài khoản' },
];

const FACULTY_KEYS = ['/faculty-approval', '/teaching-assignments'];

const MainLayout = ({ children, auth, onLogout }) => {
  const navigate = useNavigate();
  const location = useLocation();

  const visibleItems = auth?.role === 'FACULTY'
    ? ALL_ITEMS.filter(i => FACULTY_KEYS.includes(i.key))
    : ALL_ITEMS;

  const currentTitle = ALL_ITEMS.find(item => item.key === location.pathname)?.label || 'Tổng quan';

  // Chiều cao chuẩn cho cả Header và Logo Area để tạo sự thẳng hàng
  const HEADER_HEIGHT = 84;
  const FOOTER_HEIGHT = 48;

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden', background: '#ffffff' }}>

      {/* === SIDEBAR === */}
      <Sider
        width={260}
        style={{
          background: '#ffffff',
          height: '100vh',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          border: 'none',
          paddingLeft: 12,
        }}
      >
        {/* LOGO AREA - Căn giữa theo trục dọc chính xác với Header bên kia */}
        <div style={{
          height: HEADER_HEIGHT,
          flexShrink: 0,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center', // Căn giữa nội dung theo chiều dọc
          paddingLeft: 16, // Padding nội bộ logo
        }}>
          {/* Dòng 1: TimetableX. */}
          <div style={{
            fontSize: '24px',
            fontWeight: 800, // ExtraBold
            color: '#005a8d',
            letterSpacing: '-0.8px',
            lineHeight: 1, // Line-height 1 để các dòng khít nhau
            marginBottom: 4, // Khoảng cách nhỏ với dòng dưới
          }}>
            Timetable<span style={{ color: '#1f1f1f' }}>X</span>
            <span style={{ color: '#ff4d4f', fontSize: '28px', lineHeight: 0 }}>.</span>
            {/* Dấu chấm màu đỏ cam tạo điểm nhấn (Accent), line-height 0 để không đẩy dòng */}
          </div>

          {/* Dòng 2: PHENIKAA UNIVERSITY */}
          <div style={{
            fontSize: '10px',
            fontWeight: 600,
            color: '#999',
            textTransform: 'uppercase',
            marginLeft: 4,
            letterSpacing: '1.5px', // Kéo giãn chữ bé ra cho sang
            lineHeight: 1
          }}>
            Phenikaa University
          </div>
        </div>

        {/* MENU - chiều cao cố định để scrollbar hoạt động */}
        <div
          className="sidebar-menu-scroll"
          style={{
            height: `calc(100vh - ${HEADER_HEIGHT}px - ${FOOTER_HEIGHT}px)`,
            overflowY: 'auto',
            overflowX: 'hidden',
            paddingTop: 8,
          }}
        >
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            onClick={(e) => navigate(e.key)}
            style={{ border: 'none', background: 'transparent', fontWeight: 500 }}
            items={visibleItems}
          />
        </div>

        {/* FOOTER NHO NHỎ */}
        <div style={{ height: FOOTER_HEIGHT, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '0 20px', fontSize: '10px', color: '#e0e0e0', fontWeight: 500 }}>
          v1.0.0 Alpha
        </div>
      </Sider>

      {/* === MAIN CONTENT === */}
      <Layout style={{ background: '#ffffff', height: '100vh', display: 'flex', flexDirection: 'column' }}>

        {/* HEADER - Cùng chiều cao với Logo Area */}
        <Header style={{
          height: HEADER_HEIGHT,
          flexShrink: 0,
          padding: '0 40px', // Padding 2 bên rộng rãi
          background: '#ffffff',
          display: 'flex',
          alignItems: 'center', // Căn giữa theo trục dọc (Vertical Center)
          justifyContent: 'space-between',
        }}>

          {/* TIÊU ĐỀ TRANG - Sẽ nằm chính giữa trục dọc so với khối Logo bên trái */}
          <span style={{
            fontSize: '22px',
            fontWeight: 600, // SemiBold
            color: '#1f1f1f',
            letterSpacing: '-0.5px'
          }}>
            {currentTitle}
          </span>

          {/* USER PROFILE - click/hover mới hiện menu Đăng xuất */}
          <Dropdown
            trigger={['click']}
            styles={{ root: { minWidth: 160 } }}
            menu={onLogout ? {
              items: [
                {
                  key: 'logout',
                  label: 'Đăng xuất',
                  onClick: onLogout,
                },
              ],
            } : { items: [] }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}>
              {/* Text Info */}
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
                <span style={{
                  fontSize: '14px',
                  fontWeight: 600,
                  color: '#333',
                  lineHeight: '1.2'
                }}>
                  {auth?.username || 'User'}
                </span>
                <span style={{
                  fontSize: '11px',
                  fontWeight: 400,
                  color: '#888',
                  marginTop: 2,
                  lineHeight: '1.2'
                }}>
                  {auth?.role === 'FACULTY'
                    ? (auth.facultyName || 'Khoa/Viện')
                    : 'Phòng Đào tạo'}
                </span>
              </div>

              {/* Avatar */}
              <div style={{
                width: 42, height: 42,
                borderRadius: '50%',
                background: '#f0f2f5',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: '#005a8d',
                fontWeight: 700,
                fontSize: '16px',
                border: '2px solid #ffffff',
                boxShadow: '0 2px 8px rgba(0,0,0,0.05)'
              }}>
                {auth?.username ? auth.username.charAt(0).toUpperCase() : 'A'}
              </div>
            </div>
          </Dropdown>
        </Header>

        {/* CONTENT */}
        <Content
          style={{
            flex: 1,
            overflowY: 'auto',
            overflowX: 'hidden',
            padding: '0 40px 40px 40px',
            background: '#ffffff',
          }}
        >
          {children}
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;