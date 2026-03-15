import React, { useState } from 'react';
import { ConfigProvider, App as AntApp } from 'antd';
import { Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import FacultyManagement from './components/FacultyManagement';
import MajorManagement from './components/MajorManagement';
import ClassManagement from './components/ClassManagement';
import CohortManagement from './components/CohortManagement';
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

// P.ĐT bị chặn khỏi các path này (chỉ Khoa)
const FACULTY_ONLY_PATHS = ['/faculty-approval', '/teaching-assignments'];

// Khoa bị chặn khỏi các path này. Khoa được: /lecturers, /courses, /majors, /classes, /curriculum
const PDT_ONLY_PATHS = [
  '/faculties', '/rooms', '/timeslots', '/semesters', '/course-offerings',
  '/class-sections', '/support-requests', '/timetable', '/users',
];

const ProtectedRoute = ({ path, children, auth }) => {
  if (!auth) return <Navigate to="/" replace />;
  const isFaculty = auth.role === 'FACULTY';
  if (isFaculty && PDT_ONLY_PATHS.includes(path)) {
    return <Navigate to={HOME_REDIRECT.FACULTY} replace />;
  }
  if (!isFaculty && FACULTY_ONLY_PATHS.includes(path)) {
    return <Navigate to={HOME_REDIRECT.DEFAULT} replace />;
  }
  return children;
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
            <Route path="/dashboard" element={<ProtectedRoute path="/dashboard" auth={auth}><Dashboard auth={auth} /></ProtectedRoute>} />
            <Route path="/faculties" element={<ProtectedRoute path="/faculties" auth={auth}><FacultyManagement /></ProtectedRoute>} />
            <Route path="/majors" element={<ProtectedRoute path="/majors" auth={auth}><MajorManagement /></ProtectedRoute>} />
            <Route path="/classes" element={<ProtectedRoute path="/classes" auth={auth}><ClassManagement /></ProtectedRoute>} />
            <Route path="/cohorts" element={<ProtectedRoute path="/cohorts" auth={auth}><CohortManagement /></ProtectedRoute>} />
            <Route path="/rooms" element={<ProtectedRoute path="/rooms" auth={auth}><RoomManagement /></ProtectedRoute>} />
            <Route path="/timeslots" element={<ProtectedRoute path="/timeslots" auth={auth}><TimeManagement /></ProtectedRoute>} />
            <Route path="/courses" element={<ProtectedRoute path="/courses" auth={auth}><CourseManagement /></ProtectedRoute>} />
            <Route path="/curriculum" element={<ProtectedRoute path="/curriculum" auth={auth}><CurriculumManagement /></ProtectedRoute>} />
            <Route path="/lecturers" element={<ProtectedRoute path="/lecturers" auth={auth}><LecturerManagement /></ProtectedRoute>} />
            <Route path="/semesters" element={<ProtectedRoute path="/semesters" auth={auth}><SemesterManagement /></ProtectedRoute>} />
            <Route path="/course-offerings" element={<ProtectedRoute path="/course-offerings" auth={auth}><CourseOfferingManagement /></ProtectedRoute>} />
            <Route path="/class-sections" element={<ProtectedRoute path="/class-sections" auth={auth}><ClassSectionManagement /></ProtectedRoute>} />
            <Route path="/support-requests" element={<ProtectedRoute path="/support-requests" auth={auth}><SupportRequestManagement /></ProtectedRoute>} />
            <Route path="/timetable" element={<ProtectedRoute path="/timetable" auth={auth}><TimetableManagement /></ProtectedRoute>} />
            <Route path="/users" element={<ProtectedRoute path="/users" auth={auth}><UserManagement /></ProtectedRoute>} />

            {/* Khoa/Viện + P.ĐT */}
            <Route path="/faculty-approval" element={<ProtectedRoute path="/faculty-approval" auth={auth}><FacultyApprovalPlan auth={auth} /></ProtectedRoute>} />
            <Route path="/teaching-assignments" element={<ProtectedRoute path="/teaching-assignments" auth={auth}><TeachingAssignmentManagement auth={auth} /></ProtectedRoute>} />

            {/* Fallback */}
            <Route path="*" element={<Navigate to={homeRoute} replace />} />
          </Routes>
        </MainLayout>
      </AntApp>
    </ConfigProvider>
  );
}

export default App;