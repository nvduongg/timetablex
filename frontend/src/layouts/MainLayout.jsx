import React, { useMemo, useState, useEffect } from 'react';
import { Layout, Menu, Dropdown } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';

const { Header, Content, Sider } = Layout;

// Map path -> label (dùng cho tiêu đề header)
const PATH_TO_LABEL = {
  '/dashboard': 'Tổng quan',
  '/faculties': 'Quản lý Khoa/Viện',
  '/departments': 'Quản lý Bộ môn',
  '/majors': 'Quản lý Ngành',
  '/classes': 'Quản lý Lớp',
  '/cohorts': 'Quản lý Niên khóa',
  '/rooms': 'Quản lý Phòng học',
  '/timeslots': 'Quản lý Ca học',
  '/courses': 'Quản lý Môn học',
  '/curriculum': 'Quản lý CTĐT',
  '/lecturers': 'Quản lý Giảng viên',
  '/semesters': 'Quản lý Học kỳ',
  '/course-offerings': 'Kế hoạch mở lớp',
  '/faculty-approval': 'Duyệt kế hoạch',
  '/class-sections': 'Quản lý Lớp học phần',
  '/teaching-assignments': 'Phân công giảng dạy',
  '/support-requests': 'Yêu cầu hỗ trợ GV',
  '/timetable': 'Xếp Thời khóa biểu',
  '/users': 'Quản lý Tài khoản',
};

/**
 * Menu theo phân quyền: P.ĐT vs Khoa/Viện
 * P.ĐT: Dữ liệu cơ sở (đầy đủ), Lập kế hoạch, Lớp học phần, Xếp TKB, Hệ thống
 * Khoa: Dữ liệu cơ sở (Giảng viên, Môn học), Duyệt kế hoạch, Phân công giảng dạy
 */
const getMenuItems = (role) => {
  const isFaculty = role === 'FACULTY';

  const menuItems = [
    { key: '/dashboard', label: 'Tổng quan' },
  ];

  if (!isFaculty) {
    // P.ĐT: Dữ liệu cơ sở
    menuItems.push({
      key: 'data',
      label: 'Dữ liệu cơ sở',
      children: [
        { key: '/faculties', label: 'Khoa/Viện' },
        { key: '/departments', label: 'Bộ môn' },
        { key: '/majors', label: 'Ngành' },
        { key: '/cohorts', label: 'Niên khóa' },
        { key: '/classes', label: 'Lớp' },
        { key: '/rooms', label: 'Phòng học' },
        { key: '/timeslots', label: 'Ca học' },
        { key: '/courses', label: 'Môn học' },
        { key: '/curriculum', label: 'CTĐT' },
        { key: '/lecturers', label: 'Giảng viên' },
        { key: '/semesters', label: 'Học kỳ' },
      ],
    });

    // Lập kế hoạch & Dự kiến (P.ĐT: Kế hoạch mở lớp)
    menuItems.push({
      key: 'module1',
      label: 'Lập kế hoạch & Dự kiến',
      children: [
        { key: '/course-offerings', label: 'Kế hoạch mở lớp' },
        { key: '/faculty-approval', label: 'Duyệt kế hoạch' },
      ],
    });
  } else {
    // Khoa: Dữ liệu cơ sở (Giảng viên, Môn học, Ngành, Lớp, CTĐT)
    menuItems.push({
      key: 'data',
      label: 'Dữ liệu cơ sở',
      children: [
        { key: '/lecturers', label: 'Giảng viên' },
        { key: '/courses', label: 'Môn học' },
        { key: '/majors', label: 'Ngành' },
        { key: '/classes', label: 'Lớp' },
        { key: '/curriculum', label: 'CTĐT' },
      ],
    });
    menuItems.push({
      key: 'module1',
      label: 'Lập kế hoạch & Dự kiến',
      children: [
        { key: '/faculty-approval', label: 'Duyệt kế hoạch' },
      ],
    });
  }

  if (!isFaculty) {
    menuItems.push({
      key: 'module2',
      label: 'Quản lý Lớp học phần',
      children: [
        { key: '/class-sections', label: 'Lớp học phần' },
      ],
    });
    // P.ĐT: Phân công giảng dạy (tạm thời)
    menuItems.push({
      key: 'module3',
      label: 'Phân công giảng dạy',
      children: [
        { key: '/teaching-assignments', label: 'Phân công giảng dạy' },
      ],
    });
  } else {
    // Khoa: Phân công giảng dạy
    menuItems.push({
      key: 'module3',
      label: 'Phân công giảng dạy',
      children: [
        { key: '/teaching-assignments', label: 'Phân công giảng dạy' },
      ],
    });
  }

  if (!isFaculty) {
    menuItems.push({
      key: 'module4',
      label: 'Xếp Thời khóa biểu',
      children: [
        { key: '/timetable', label: 'Xếp TKB' },
      ],
    });

    menuItems.push({
      key: 'system',
      label: 'Hệ thống',
      children: [
        { key: '/support-requests', label: 'Yêu cầu hỗ trợ GV' },
        { key: '/users', label: 'Quản lý Tài khoản' },
      ],
    });
  }

  return menuItems;
};

const MainLayout = ({ children, auth, onLogout }) => {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = useMemo(() => getMenuItems(auth?.role), [auth?.role]);

  // Mở submenu tương ứng với path hiện tại
  const pathBasedOpenKeys = useMemo(() => {
    const path = location.pathname;
    if (path.startsWith('/course-offerings') || path.startsWith('/faculty-approval')) return ['module1'];
    if (path.startsWith('/class-sections')) return ['module2'];
    if (path.startsWith('/teaching-assignments')) return ['module3'];
    if (path.startsWith('/timetable')) return ['module4'];
    if (path.startsWith('/support-requests') || path.startsWith('/users')) return ['system'];
    if (['/faculties','/departments','/majors','/classes','/cohorts','/rooms','/timeslots','/courses','/curriculum','/lecturers','/semesters'].includes(path)) return ['data'];
    return [];
  }, [location.pathname]);

  const [openKeys, setOpenKeys] = useState(pathBasedOpenKeys);
  useEffect(() => {
    setOpenKeys(prev => Array.from(new Set([...prev, ...pathBasedOpenKeys])));
  }, [pathBasedOpenKeys]);

  const currentTitle = PATH_TO_LABEL[location.pathname] || 'Tổng quan';

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
            className="main-sidebar-menu"
            mode="inline"
            selectedKeys={[location.pathname]}
            openKeys={openKeys}
            onOpenChange={setOpenKeys}
            onClick={(e) => { if (e.key.startsWith('/')) navigate(e.key); }}
            style={{ border: 'none', background: 'transparent', fontWeight: 500 }}
            items={menuItems}
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