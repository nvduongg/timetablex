import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Row, Col, Card, Typography, Button, Spin, Statistic, Space, Flex } from 'antd';
import {
    BookOutlined,
    ScheduleOutlined,
    TeamOutlined,
    BankOutlined,
    AppstoreOutlined,
    CheckCircleOutlined,
    FileTextOutlined,
    UserOutlined,
} from '@ant-design/icons';
import {
    PieChart,
    Pie,
    Cell,
    ResponsiveContainer,
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
} from 'recharts';
import * as SemesterService from '../services/semesterService';
import * as OfferingService from '../services/offeringService';
import * as FacultyService from '../services/facultyService';
import * as LecturerService from '../services/lecturerService';
import * as CourseService from '../services/courseService';
import * as ClassSectionService from '../services/classSectionService';

const { Title, Text } = Typography;

const PRIMARY = '#005a8d';
const SUCCESS = '#52c41a';
const WARNING = '#fa8c16';
const DANGER = '#f5222d';
const GREY = '#8c8c8c';
const BORDER = '#eef0f3';

const StatCard = ({ icon, iconBg, iconColor, title, value, subtitle, loading }) => (
    <Card
        variant="borderless"
        style={{ borderRadius: 12, height: '100%', border: `1px solid ${BORDER}` }}
        styles={{ body: { padding: 20 } }}
    >
        <Flex justify="space-between" align="flex-start">
            <div>
                <Text type="secondary" style={{ fontSize: 12 }}>{title}</Text>
                <div style={{ marginTop: 4 }}>
                    {loading ? (
                        <Spin size="small" />
                    ) : (
                        <Statistic value={value} valueStyle={{ fontSize: 28, fontWeight: 700, color: '#1a1a1a' }} />
                    )}
                </div>
                {subtitle && <Text type="secondary" style={{ fontSize: 11, marginTop: 4, display: 'block' }}>{subtitle}</Text>}
            </div>
            <div style={{
                width: 48, height: 48, borderRadius: 12,
                background: iconBg, display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: iconColor, fontSize: 22, flexShrink: 0,
            }}>
                {icon}
            </div>
        </Flex>
    </Card>
);

const Dashboard = ({ auth }) => {
    const navigate = useNavigate();
    const isFaculty = auth?.role === 'FACULTY';
    const facultyId = auth?.facultyId || null;

    const [loading, setLoading] = useState(true);
    const [semesters, setSemesters] = useState([]);
    const [activeSemester, setActiveSemester] = useState(null);
    const [stats, setStats] = useState({
        faculties: 0,
        lecturers: 0,
        courses: 0,
        classSections: 0,
    });
    const [offeringByStatus, setOfferingByStatus] = useState([]);
    const [sectionsByFaculty, setSectionsByFaculty] = useState([]);

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            try {
                const [semRes, facRes, lecRes, courseRes] = await Promise.all([
                    SemesterService.getSemesters(),
                    FacultyService.getFaculties(),
                    LecturerService.getLecturers(facultyId),
                    CourseService.getCourses(),
                ]);
                const sems = semRes.data || [];
                const active = sems.find(s => s.isActive);
                setSemesters(sems);
                setActiveSemester(active);

                setStats(prev => ({
                    ...prev,
                    faculties: isFaculty ? 1 : (facRes.data?.length || 0),
                    lecturers: lecRes.data?.length || 0,
                    courses: courseRes.data?.length || 0,
                }));

                if (active) {
                    const [offerRes, sectRes] = await Promise.all([
                        OfferingService.getOfferingsBySemester(active.id, facultyId ? { facultyId } : {}),
                        ClassSectionService.getClassSectionsBySemester(active.id, facultyId),
                    ]);
                    const offerings = offerRes.data || [];
                    const sections = sectRes.data || [];

                    const byStatus = {};
                    offerings.forEach(o => {
                        const s = (o.status || 'DRAFT').toUpperCase();
                        byStatus[s] = (byStatus[s] || 0) + 1;
                    });
                    setOfferingByStatus([
                        { name: 'Nháp', value: byStatus.DRAFT || 0, color: GREY },
                        { name: 'Chờ duyệt', value: byStatus.WAITING_APPROVAL || 0, color: WARNING },
                        { name: 'Đã duyệt', value: byStatus.APPROVED || 0, color: SUCCESS },
                        { name: 'Từ chối', value: byStatus.REJECTED || 0, color: DANGER },
                    ].filter(d => d.value > 0));

                    setStats(prev => ({ ...prev, classSections: sections.length }));

                    if (!isFaculty && sections.length > 0) {
                        const byFac = {};
                        sections.forEach(sec => {
                            const off = sec.courseOffering || sec.offering;
                            const name = off?.faculty?.name || off?.faculty?.code || 'Khác';
                            if (off?.faculty?.id) byFac[name] = (byFac[name] || 0) + 1;
                        });
                        setSectionsByFaculty(Object.entries(byFac).map(([name, count]) => ({ name, count })));
                    }
                }
            } catch {
                setStats({ faculties: 0, lecturers: 0, courses: 0, classSections: 0 });
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [isFaculty, facultyId]);

    const displayName = isFaculty ? (auth?.facultyName || 'Khoa/Viện') : 'Phòng Đào tạo';

    return (
        <div style={{ paddingTop: 8 }}>
            {/* Welcome */}
            <div style={{ marginBottom: 24 }}>
                <Title level={4} style={{ margin: 0, fontWeight: 600 }}>
                    Xin chào, {displayName}
                </Title>
                <Text type="secondary" style={{ fontSize: 14 }}>
                    {activeSemester
                        ? `Học kỳ hiện tại: ${activeSemester.name}`
                        : 'Chưa có học kỳ đang hoạt động'}
                </Text>
            </div>

            {/* Stat cards */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                {!isFaculty && (
                    <Col xs={24} sm={12} md={6}>
                        <StatCard
                            icon={<BankOutlined />}
                            iconBg="#e6f4ff"
                            iconColor={PRIMARY}
                            title="Khoa/Viện"
                            value={stats.faculties}
                            loading={loading}
                        />
                    </Col>
                )}
                <Col xs={24} sm={12} md={6}>
                    <StatCard
                        icon={<UserOutlined />}
                        iconBg="#f6ffed"
                        iconColor={SUCCESS}
                        title="Giảng viên"
                        value={stats.lecturers}
                        loading={loading}
                    />
                </Col>
                <Col xs={24} sm={12} md={6}>
                    <StatCard
                        icon={<BookOutlined />}
                        iconBg="#fff7e6"
                        iconColor={WARNING}
                        title="Môn học"
                        value={stats.courses}
                        loading={loading}
                    />
                </Col>
                <Col xs={24} sm={12} md={6}>
                    <StatCard
                        icon={<AppstoreOutlined />}
                        iconBg="#f9f0ff"
                        iconColor="#722ed1"
                        title="Lớp học phần"
                        value={stats.classSections}
                        subtitle={activeSemester ? `HK ${activeSemester.name}` : ''}
                        loading={loading}
                    />
                </Col>
            </Row>

            {/* Charts */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                {offeringByStatus.length > 0 && (
                    <Col xs={24} md={12}>
                        <Card
                            title={
                                <Space>
                                    <ScheduleOutlined style={{ color: PRIMARY }} />
                                    <span>Trạng thái kế hoạch mở lớp</span>
                                </Space>
                            }
                            variant="borderless"
                            style={{ borderRadius: 12, border: `1px solid ${BORDER}` }}
                        >
                            <ResponsiveContainer width="100%" height={220}>
                                <PieChart>
                                    <Pie
                                        data={offeringByStatus}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={50}
                                        outerRadius={80}
                                        paddingAngle={2}
                                        dataKey="value"
                                        nameKey="name"
                                        label={({ name, value }) => `${name}: ${value}`}
                                    >
                                        {offeringByStatus.map((entry, i) => (
                                            <Cell key={i} fill={entry.color} />
                                        ))}
                                    </Pie>
                                    <Tooltip formatter={(v) => [v, 'Học phần']} />
                                </PieChart>
                            </ResponsiveContainer>
                        </Card>
                    </Col>
                )}
                {sectionsByFaculty.length > 0 && (
                    <Col xs={24} md={12}>
                        <Card
                            title={
                                <Space>
                                    <TeamOutlined style={{ color: PRIMARY }} />
                                    <span>Lớp học phần theo Khoa</span>
                                </Space>
                            }
                            variant="borderless"
                            style={{ borderRadius: 12, border: `1px solid ${BORDER}` }}
                        >
                            <ResponsiveContainer width="100%" height={220}>
                                <BarChart data={sectionsByFaculty} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                                    <XAxis dataKey="name" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                                    <YAxis tick={{ fontSize: 11 }} axisLine={false} tickLine={false} allowDecimals={false} />
                                    <Tooltip />
                                    <Bar dataKey="count" fill={PRIMARY} radius={[4, 4, 0, 0]} name="Lớp" />
                                </BarChart>
                            </ResponsiveContainer>
                        </Card>
                    </Col>
                )}
            </Row>

            {/* Quick actions */}
            <Card
                title="Thao tác nhanh"
                variant="borderless"
                style={{ borderRadius: 12, border: `1px solid ${BORDER}` }}
            >
                <Row gutter={[16, 12]}>
                    {!isFaculty && (
                        <Col xs={24} sm={12} md={8}>
                            <Button
                                type="primary"
                                block
                                size="large"
                                icon={<ScheduleOutlined />}
                                onClick={() => navigate('/course-offerings')}
                                style={{ height: 48, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                            >
                                Kế hoạch mở lớp
                            </Button>
                        </Col>
                    )}
                    {isFaculty && (
                        <Col xs={24} sm={12} md={8}>
                            <Button
                                block
                                size="large"
                                icon={<CheckCircleOutlined />}
                                onClick={() => navigate('/faculty-approval')}
                                style={{ height: 48, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                            >
                                Duyệt kế hoạch
                            </Button>
                        </Col>
                    )}
                    {!isFaculty && (
                        <Col xs={24} sm={12} md={8}>
                            <Button
                                block
                                size="large"
                                icon={<AppstoreOutlined />}
                                onClick={() => navigate('/class-sections')}
                                style={{ height: 48, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                            >
                                Lớp học phần
                            </Button>
                        </Col>
                    )}
                    {isFaculty && (
                        <Col xs={24} sm={12} md={8}>
                            <Button
                                block
                                size="large"
                                icon={<TeamOutlined />}
                                onClick={() => navigate('/teaching-assignments')}
                                style={{ height: 48, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                            >
                                Phân công giảng dạy
                            </Button>
                        </Col>
                    )}
                    {!isFaculty && (
                        <Col xs={24} sm={12} md={8}>
                            <Button
                                block
                                size="large"
                                icon={<FileTextOutlined />}
                                onClick={() => navigate('/timetable')}
                                style={{ height: 48, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                            >
                                Xếp thời khóa biểu
                            </Button>
                        </Col>
                    )}
                </Row>
            </Card>
        </div>
    );
};

export default Dashboard;
