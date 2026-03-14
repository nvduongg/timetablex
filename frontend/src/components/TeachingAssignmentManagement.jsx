import React, { useState, useEffect } from 'react';
import { App, Table, Select, Tag, Space, Alert, Tooltip, Button, Flex, Typography, Modal, Input } from 'antd';
import { UserOutlined, ThunderboltOutlined, BarChartOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import * as SemesterService from '../services/semesterService';
import * as FacultyService from '../services/facultyService';
import * as ClassSectionService from '../services/classSectionService';
import * as LecturerService from '../services/lecturerService';
import TeachingLoadStatsModal from './TeachingLoadStatsModal';

const { Option } = Select;
const { Text } = Typography;

/**
 * Module 3: Phân công giảng dạy – Khoa/Viện gán Giảng viên vào từng lớp học phần.
 * Có tùy chọn "Bỏ qua" để phân công sau khi có TKB dự kiến (nếu Khoa được phép).
 */
const TeachingAssignmentManagement = ({ auth }) => {
    const { message } = App.useApp();
    const isFaculty = auth?.role === 'FACULTY';
    const defaultFacultyId = auth?.facultyId || null;

    const [sections, setSections] = useState([]);
    const [semesters, setSemesters] = useState([]);
    const [faculties, setFaculties] = useState([]);
    const [lecturers, setLecturers] = useState([]);
    const [currentSemesterId, setCurrentSemesterId] = useState(null);
    const [currentFacultyId, setCurrentFacultyId] = useState(defaultFacultyId);
    const [loading, setLoading] = useState(false);
    const [skipAllowed, setSkipAllowed] = useState(false);
    const [teachingLoad, setTeachingLoad] = useState([]);
    const [statsModalOpen, setStatsModalOpen] = useState(false);
    const [supportModalOpen, setSupportModalOpen] = useState(false);
    const [selectedSectionForSupport, setSelectedSectionForSupport] = useState(null);
    const [supportComment, setSupportComment] = useState('');
    const [selectedRowKeys, setSelectedRowKeys] = useState([]);
    const [statusFilter, setStatusFilter] = useState('ALL');

    useEffect(() => {
        SemesterService.getSemesters().then(res => {
            setSemesters(res.data || []);
            const active = (res.data || []).find(s => s.isActive);
            if (active) setCurrentSemesterId(active.id);
        });
        if (!isFaculty) FacultyService.getFaculties().then(res => setFaculties(res.data || []));
    }, [isFaculty]);

    useEffect(() => {
        if (isFaculty && defaultFacultyId) setCurrentFacultyId(defaultFacultyId);
    }, [isFaculty, defaultFacultyId]);

    const fetchSections = async () => {
        if (!currentSemesterId || (!currentFacultyId && isFaculty)) return;
        setLoading(true);
        try {
            const res = await ClassSectionService.getClassSectionsBySemester(currentSemesterId, currentFacultyId);
            const data = res.data || [];
            setSections(data);
            setSelectedRowKeys([]);
        } catch {
            message.error('Lỗi tải danh sách lớp học phần');
        } finally {
            setLoading(false);
        }
    };

    const fetchLecturers = async () => {
        if (!currentFacultyId) return;
        try {
            const res = await LecturerService.getLecturers(currentFacultyId, {
                semesterId: currentSemesterId,
                forAssignment: true
            });
            setLecturers(res.data || []);
        } catch {
            setLecturers([]);
        }
    };

    const fetchTeachingLoad = async () => {
        if (!currentSemesterId) return;
        if (isFaculty && !currentFacultyId) return;
        try {
            const res = await ClassSectionService.getTeachingLoad(currentSemesterId, currentFacultyId);
            setTeachingLoad(res.data || []);
        } catch {
            setTeachingLoad([]);
        }
    };

    useEffect(() => {
        fetchSections();
        if (currentFacultyId) {
            fetchLecturers();
            fetchTeachingLoad();
            ClassSectionService.isFacultySkipAllowed(currentFacultyId)
                .then(r => setSkipAllowed(r.data?.allowed ?? false))
                .catch(() => setSkipAllowed(false));
        }
    }, [currentSemesterId, currentFacultyId]);

    const handleAutoAssign = async () => {
        if (!currentSemesterId || (!currentFacultyId && isFaculty)) {
            message.warning('Vui lòng chọn học kỳ và Khoa');
            return;
        }
        setLoading(true);
        try {
            const res = await ClassSectionService.autoAssignLecturers(currentSemesterId, currentFacultyId);
            const count = res.data?.assignedCount ?? 0;
            message.success(res.data?.message || `Đã tự động phân công ${count} lớp`);
            fetchSections();
            fetchTeachingLoad();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi tự động phân công');
        } finally {
            setLoading(false);
        }
    };

    const handleAssign = async (sectionId, lecturerId, skip) => {
        try {
            await ClassSectionService.assignLecturerToSection(sectionId, {
                lecturerId: skip ? null : lecturerId,
                skipAssignment: !!skip
            });
            message.success('Đã cập nhật phân công');
            fetchSections();
            fetchTeachingLoad();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi cập nhật phân công');
        }
    };

    const handleOpenStats = async () => {
        await fetchTeachingLoad();
        setStatsModalOpen(true);
    };

    const handleRequestSupport = (section) => {
        setSelectedSectionForSupport(section);
        setSupportComment('');
        setSupportModalOpen(true);
    };

    const handleSubmitSupportRequest = async () => {
        if (!selectedSectionForSupport) return;
        try {
            const ids = selectedSectionForSupport._bulkIds || [selectedSectionForSupport.id];
            await Promise.all(ids.map(id => ClassSectionService.requestSupport(id, supportComment)));
            message.success(`Đã gửi yêu cầu hỗ trợ GV cho ${ids.length} lớp tới Khoa quản lý chuyên môn`);
            setSupportModalOpen(false);
            setSelectedSectionForSupport(null);
            setSupportComment('');
            setSelectedRowKeys([]);
            fetchSections();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi gửi yêu cầu hỗ trợ');
        }
    };

    const handleBulkRequestSupport = () => {
        const target = sections.filter(s =>
            selectedRowKeys.includes(s.id) && !s.lecturer && !s.skipAssignment && !s.needsSupport
        );
        if (target.length === 0) {
            message.info('Chọn các lớp chưa phân công để gửi yêu cầu hỗ trợ');
            return;
        }
        setSupportComment('');
        setSelectedSectionForSupport({ ...target[0], _bulkIds: target.map(t => t.id) });
        setSupportModalOpen(true);
    };

    const filteredSections = sections.filter(s => {
        switch (statusFilter) {
            case 'UNASSIGNED':
                return !s.lecturer && !s.skipAssignment && !s.needsSupport;
            case 'ASSIGNED':
                return !!s.lecturer;
            case 'SKIPPED':
                return !!s.skipAssignment;
            case 'NEED_SUPPORT':
                return !!s.needsSupport && !s.lecturer;
            default:
                return true;
        }
    });

    const columns = [
        { title: 'Mã lớp HP', dataIndex: 'code', width: 130, render: t => <span style={{ fontWeight: 600, color: '#005a8d' }}>{t}</span> },
        {
            title: 'Loại', dataIndex: 'sectionType', width: 90,
            render: t => <Tag color={t === 'LT' ? 'geekblue' : 'purple'} style={{ border: 'none' }}>{t === 'LT' ? 'LT' : 'TH'}</Tag>
        },
        {
            title: 'Học phần', key: 'course',
            render: (_, r) => (
                <Space orientation="vertical" size={0}>
                    <span style={{ fontWeight: 500 }}>{r.courseOffering?.course?.code || '—'}</span>
                    <span style={{ fontSize: 12, color: '#666' }}>{r.courseOffering?.course?.name || '—'}</span>
                </Space>
            )
        },
        {
            title: 'Giảng viên phân công', key: 'lecturer', width: 380,
            render: (_, r) => {
                const courseId = r.courseOffering?.course?.id;
                const courseOfferingFacultyId = r.courseOffering?.faculty?.id;
                const courseManagingFacultyId = r.courseOffering?.course?.faculty?.id;
                const isFromOtherFaculty = r.needsSupport && courseOfferingFacultyId !== currentFacultyId;
                const referenceFacultyId = r.needsSupport ? courseManagingFacultyId : courseOfferingFacultyId;

                const qualifiedLecturers = lecturers.filter(l =>
                    l.courses && l.courses.some(c => c.id === courseId)
                );
                const isOwnFaculty = (l) => l.faculty?.id === referenceFacultyId || l.faculty?.id === currentFacultyId;
                const ownFacultyLecturers = qualifiedLecturers.filter(isOwnFaculty);
                const otherFacultyLecturers = qualifiedLecturers.filter(l => !isOwnFaculty(l));

                return (
                    <Space orientation="vertical" size={4} style={{ width: '100%' }}>
                        {isFromOtherFaculty && (
                            <div style={{ marginBottom: 4 }}>
                                <Tag color="orange" icon={<QuestionCircleOutlined />} style={{ fontSize: 11 }}>
                                    Yêu cầu hỗ trợ từ {r.courseOffering?.faculty?.name}
                                </Tag>
                                {r.supportRequestComment && (
                                    <div style={{ fontSize: 11, color: '#666', fontStyle: 'italic', marginTop: 2 }}>
                                        {r.supportRequestComment}
                                    </div>
                                )}
                            </div>
                        )}
                        <Select
                            placeholder="Chọn giảng viên"
                            variant="filled"
                            style={{ width: '100%', minWidth: 250 }}
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            optionLabelProp="label" // Hiển thị tên (label) thay vì ID
                            value={r.skipAssignment ? '__SKIP__' : (r.lecturer?.id ?? '__UNASSIGNED__')}
                            onChange={(val) => {
                                if (val === '__SKIP__') handleAssign(r.id, null, true);
                                else if (val === '__UNASSIGNED__') handleAssign(r.id, null, false);
                                else handleAssign(r.id, val || null, false);
                            }}
                            popupRender={(menu) => (
                                <>
                                    {menu}
                                    {otherFacultyLecturers.length > 0 && (
                                        <div style={{ padding: '8px 12px', borderTop: '1px solid #f0f0f0', background: '#fff7e6', fontSize: 11, color: '#666' }}>
                                            <Tag color="orange" style={{ fontSize: 10, marginRight: 4 }}>Chéo khoa</Tag>
                                            GV từ khoa khác có chuyên môn dạy môn này
                                        </div>
                                    )}
                                </>
                            )}
                        >
                            {skipAllowed && <Option value="__SKIP__" label="Bỏ qua (phân công sau)"><Tag color="orange">Bỏ qua (phân công sau)</Tag></Option>}
                            <Option value="__UNASSIGNED__" label="— Chưa phân công —">— Chưa phân công —</Option>
                            {ownFacultyLecturers.length > 0 && (
                                <Option disabled style={{ fontWeight: 600, color: '#333', fontSize: 11, padding: '4px 0' }}>
                                    —— {r.needsSupport ? (r.courseOffering?.course?.faculty?.name || 'Khoa quản lý chuyên môn') : (r.courseOffering?.faculty?.name || 'Khoa chủ quản')} ——
                                </Option>
                            )}
                            {ownFacultyLecturers.map(l => (
                                <Option key={l.id} value={l.id} label={`${l.name || 'N/A'} (${l.faculty?.code || ''})`}>
                                    <Space size={4}>
                                        <span>{l.name || `GV #${l.id}`}</span>
                                        <Tag color="blue" style={{ fontSize: 10, border: 'none', margin: 0, padding: '0 4px' }}>
                                            {l.faculty?.code || ''}
                                        </Tag>
                                    </Space>
                                </Option>
                            ))}
                            {otherFacultyLecturers.length > 0 && (
                                <Option disabled style={{ fontWeight: 600, color: '#666', fontSize: 11, padding: '4px 0', marginTop: 4 }}>
                                    —— Khoa khác (dùng chung) ——
                                </Option>
                            )}
                            {otherFacultyLecturers.map(l => (
                                <Option key={l.id} value={l.id} label={`${l.name || 'N/A'} (${l.faculty?.code || ''})`}>
                                    <Space size={4}>
                                        <span>{l.name || `GV #${l.id}`}</span>
                                        <Tag color="orange" style={{ fontSize: 10, border: 'none', margin: 0, padding: '0 4px' }}>
                                            {l.faculty?.code || ''}
                                        </Tag>
                                    </Space>
                                </Option>
                            ))}
                            {qualifiedLecturers.length === 0 && (
                                <Option disabled style={{ color: '#999', fontSize: 12 }}>
                                    Không có GV nào có chuyên môn dạy môn này
                                </Option>
                            )}
                        </Select>
                    </Space>
                );
            }
        },
        {
            title: 'Trạng thái', key: 'status', width: 130,
            render: (_, r) => {
                if (r.skipAssignment) return <Tag color="orange">Bỏ qua</Tag>;
                if (r.needsSupport) return <Tag color="geekblue">Đã gửi Y/C Hỗ trợ</Tag>;
                if (r.lecturer) return <Tag color="green">Đã phân công</Tag>;
                return <Tag>Chưa phân công</Tag>;
            }
        }
    ];

    const currentSemester = semesters.find(s => s.id === currentSemesterId);
    const currentFaculty = faculties.find(f => f.id === currentFacultyId);

    return (
        <div style={{ width: '100%' }}>
            <Flex
                justify="space-between"
                align="center"
                wrap="nowrap"
                style={{
                    marginBottom: 24,
                }}
            >
                <Space size={8}>
                    {/* Học kỳ */}
                    <Select
                        variant="filled"
                        style={{ width: 'auto' }}
                        value={currentSemesterId}
                        onChange={setCurrentSemesterId}
                        placeholder="Học kỳ"
                        optionLabelProp="label"
                    >
                        {semesters.map(s => (
                            <Option key={s.id} value={s.id} label={s.name}>
                                <Flex justify="space-between" align="center" style={{ width: '100%' }}>
                                    <Text>{s.name}</Text>
                                    {s.isActive && (
                                        <Text style={{ fontSize: 11, color: '#52c41a', display: 'flex', alignItems: 'center', gap: 4 }}>
                                            <span style={{ fontSize: 14 }}>●</span> Hiện tại
                                        </Text>
                                    )}
                                </Flex>
                            </Option>
                        ))}
                    </Select>

                    {/* Chọn Khoa (Nếu là PĐT) */}
                    {!isFaculty ? (
                        <Select
                            variant="filled"
                            style={{ width: 200 }}
                            value={currentFacultyId}
                            onChange={setCurrentFacultyId}
                            placeholder="Chọn Khoa/Viện"
                            showSearch
                            optionFilterProp="children"
                        >
                            {faculties.map(f => <Option key={f.id} value={f.id}>{f.name} ({f.code})</Option>)}
                        </Select>
                    ) : (
                        currentFacultyId && (
                            <Tag
                                style={{
                                    margin: 0,
                                    height: 38,
                                    display: 'flex',
                                    alignItems: 'center',
                                    padding: '0 16px',
                                    background: '#f2f4f6',
                                    border: 'none',
                                    color: '#005a8d',
                                    fontWeight: 500,
                                    fontSize: 14,
                                    borderRadius: 6
                                }}
                            >
                                {auth?.facultyName || 'Khoa của tôi'}
                            </Tag>
                        )
                    )}

                    {/* Trạng thái phân công */}
                    <Select
                        variant="filled"
                        style={{ width: 170 }}
                        value={statusFilter}
                        onChange={setStatusFilter}
                    >
                        <Option value="ALL">Tất cả trạng thái</Option>
                        <Option value="UNASSIGNED">Chưa phân công</Option>
                        <Option value="ASSIGNED">Đã phân công</Option>
                        <Option value="SKIPPED">Bỏ qua</Option>
                        <Option value="NEED_SUPPORT">Đã yêu cầu hỗ trợ</Option>
                    </Select>
                </Space>

                <Space size={8}>
                    <Tooltip title="Tự động gán giảng viên theo chuyên môn & cân bằng tải">
                        <Button
                            type="primary"
                            icon={<ThunderboltOutlined />}
                            onClick={handleAutoAssign}
                            loading={loading}
                            disabled={!currentSemesterId || (!currentFacultyId && isFaculty)}
                        >
                            Tự động gán
                        </Button>
                    </Tooltip>

                    <Button
                        icon={<QuestionCircleOutlined />}
                        onClick={handleBulkRequestSupport}
                        disabled={selectedRowKeys.length === 0}
                    >
                        Yêu cầu hỗ trợ ({selectedRowKeys.length})
                    </Button>
                    <Button
                        icon={<BarChartOutlined />}
                        onClick={handleOpenStats}
                        disabled={!currentSemesterId || (!currentFacultyId && isFaculty)}
                    >
                        Thống kê tải
                    </Button>
                </Space>
            </Flex>

            <Alert title="Module 3: Phân công giảng dạy" description="Tự động phân công dựa trên Khoa + Chuyên môn giảng viên, cân bằng tải. Sau khi chạy có thể chỉnh sửa thủ công. Hoàn thành trong 1 tuần."
                type="info" showIcon icon={<UserOutlined />} style={{ marginBottom: 20, border: 'none', background: '#e6f7ff' }} />

            <TeachingLoadStatsModal
                open={statsModalOpen}
                onClose={() => setStatsModalOpen(false)}
                teachingLoad={teachingLoad}
                semesterName={currentSemester?.name}
                facultyName={currentFaculty?.name || auth?.facultyName}
            />

            <Modal
                title="Yêu cầu hỗ trợ Giảng viên"
                open={supportModalOpen}
                onOk={handleSubmitSupportRequest}
                onCancel={() => { setSupportModalOpen(false); setSelectedSectionForSupport(null); setSupportComment(''); }}
                okText="Gửi yêu cầu"
                cancelText="Hủy"
                width={640}
            >
                {selectedSectionForSupport && (
                    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
                        <div>
                            <div style={{ fontSize: 12, fontWeight: 600, color: '#666', marginBottom: 8 }}>Lớp học phần</div>
                            <div>
                                <Tag color="blue">{selectedSectionForSupport.code}</Tag>
                                {selectedSectionForSupport.courseOffering?.course?.code} - {selectedSectionForSupport.courseOffering?.course?.name}
                            </div>
                            {selectedSectionForSupport._bulkIds?.length > 1 && (
                                <div style={{ marginTop: 6, fontSize: 12, color: '#888' }}>
                                    Áp dụng cho {selectedSectionForSupport._bulkIds.length} lớp đang chọn
                                </div>
                            )}
                        </div>
                        <div>
                            <div style={{ marginBottom: 6, fontWeight: 500 }}>Ghi chú (tùy chọn)</div>
                            <Input.TextArea
                                rows={4}
                                placeholder="Ví dụ: Khoa không có GV chuyên môn, đề xuất mượn GV từ Khoa khác..."
                                value={supportComment}
                                onChange={(e) => setSupportComment(e.target.value)}
                                maxLength={500}
                                showCount
                            />
                        </div>
                        <Alert
                            message="Quy trình"
                            description="Yêu cầu sẽ được chuyển tới Khoa quản lý chuyên môn của môn học. Chỉ Khoa đó mới có quyền phân công GV vào lớp này."
                            type="info"
                            showIcon
                        />
                    </Space>
                )}
            </Modal>

            {(!currentFacultyId && !isFaculty) ? (
                <Alert title="Chọn Khoa/Viện để xem danh sách lớp học phần cần phân công" type="warning" showIcon style={{ marginTop: 16 }} />
            ) : (
                <Table
                    dataSource={filteredSections}
                    columns={columns}
                    rowKey="id"
                    loading={loading}
                    size="middle"
                    rowSelection={{ selectedRowKeys, onChange: (keys) => setSelectedRowKeys(keys) }}
                    pagination={{ pageSize: 10, placement: 'bottomRight', showTotal: (t) => `Tổng ${t} lớp` }}
                />
            )}
        </div>
    );
};

export default TeachingAssignmentManagement;