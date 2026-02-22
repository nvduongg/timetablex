import React, { useState } from 'react';
import { ConfigProvider, App as AntApp } from 'antd';
import { Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import FacultyManagement from './components/FacultyManagement';
import MajorManagement from './components/MajorManagement';
import ClassManagement from './components/ClassManagement';
import RoomManagement from './components/RoomManagement';
import TimeManagement from './components/TimeManagement';
import CourseManagement from './components/CourseManagement';
import CurriculumManagement from './components/CurriculumManagement';
import LecturerManagement from './components/LecturerManagement';
import SemesterManagement from './components/SemesterManagement';
import CourseOfferingManagement from './components/CourseOfferingManagement';
import FacultyApprovalPlan from './components/FacultyApprovalPlan';
import ClassSectionManagement from './components/ClassSectionManagement';
import TeachingAssignmentManagement from './components/TeachingAssignmentManagement';
import SupportRequestManagement from './components/SupportRequestManagement';
import TimetableManagement from './components/TimetableManagement';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import UserManagement from './components/UserManagement';

const HOME_REDIRECT = {
  FACULTY: '/faculty-approval',
  DEFAULT: '/dashboard',
};

function App() {
  const [auth, setAuth] = useState(() => {
    const raw = localStorage.getItem('auth_user');
    return raw ? JSON.parse(raw) : null;
  });

  const handleLogout = () => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    setAuth(null);
  };

  if (!auth) {
    return <Login onLogin={(data) => setAuth(data)} />;
  }

  const homeRoute = auth.role === 'FACULTY' ? HOME_REDIRECT.FACULTY : HOME_REDIRECT.DEFAULT;

  return (
    <ConfigProvider
      theme={{
        token: {
          fontFamily: "'Poppins', sans-serif",
          colorPrimary: '#005a8d',
          borderRadius: 6,
          colorBgContainer: '#ffffff',
          colorBorder: 'transparent',
          colorSplit: 'transparent',
          boxShadow: 'none',
          fontSize: 14,
          controlHeight: 38,
        },
        components: {
          Layout: { bodyBg: '#ffffff', headerBg: '#ffffff', siderBg: '#ffffff' },
          Button: {
            boxShadow: 'none',
            primaryShadow: 'none',
            defaultBg: '#f2f4f6',
            defaultHoverBg: '#e6e9eb',
            defaultColor: '#1f1f1f',
            contentFontSize: 14,
            fontWeight: 500,
            border: 'none',
            paddingInline: 16,
          },
          Input: {
            colorBgContainer: '#f2f4f6',
            activeShadow: 'none',
            placeholderColor: '#b0b0b0',
            border: 'none',
          },
          Select: {
            colorBgContainer: '#f2f4f6',
            colorBorder: 'transparent',
            boxShadow: 'none',
          },
          Table: {
            headerBg: '#ffffff',
            headerColor: '#888888',
            headerSplitColor: 'transparent',
            borderColor: 'transparent',
            headerBorderRadius: 0,
            headerFontWeight: 600,
            fontSize: 14,
            rowHoverBg: '#f0f7ff',
            cellPaddingBlock: 12,
          },
          Menu: {
            itemBg: '#ffffff',
            itemSelectedBg: '#e6f4ff',
            itemSelectedColor: '#005a8d',
            itemColor: '#666666',
            activeBarBorderWidth: 0,
            itemMarginInline: 10,
            itemBorderRadius: 6,
            fontSize: 14,
            fontWeight: 500,
            itemHeight: 40,
          },
          Modal: {
            titleFontSize: 18,
            titleColor: '#005a8d',
            headerBg: '#ffffff',
            contentBg: '#ffffff',
            mask: { closable: true },
          },
          Tag: {
            defaultBg: '#f2f4f6',
            defaultBorder: 'transparent',
          }
        }
      }}
    >
      <AntApp>
        <MainLayout auth={auth} onLogout={handleLogout}>
          <Routes>
            <Route path="/" element={<Navigate to={homeRoute} replace />} />

            {/* Phòng Đào tạo */}
            <Route path="/dashboard" element={<Dashboard auth={auth} />} />
            <Route path="/faculties" element={<FacultyManagement />} />
            <Route path="/majors" element={<MajorManagement />} />
            <Route path="/classes" element={<ClassManagement />} />
            <Route path="/rooms" element={<RoomManagement />} />
            <Route path="/timeslots" element={<TimeManagement />} />
            <Route path="/courses" element={<CourseManagement />} />
            <Route path="/curriculum" element={<CurriculumManagement />} />
            <Route path="/lecturers" element={<LecturerManagement />} />
            <Route path="/semesters" element={<SemesterManagement />} />
            <Route path="/course-offerings" element={<CourseOfferingManagement />} />
            <Route path="/class-sections" element={<ClassSectionManagement />} />
            <Route path="/support-requests" element={<SupportRequestManagement />} />
            <Route path="/timetable" element={<TimetableManagement />} />
            <Route path="/users" element={<UserManagement />} />

            {/* Khoa/Viện + chung */}
            <Route path="/faculty-approval" element={<FacultyApprovalPlan />} />
            <Route path="/teaching-assignments" element={<TeachingAssignmentManagement auth={auth} />} />

            {/* Fallback */}
            <Route path="*" element={<Navigate to={homeRoute} replace />} />
          </Routes>
        </MainLayout>
      </AntApp>
    </ConfigProvider>
  );
}

export default App;