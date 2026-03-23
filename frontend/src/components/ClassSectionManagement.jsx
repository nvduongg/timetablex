import React, { useState, useEffect } from 'react';
import { App, Table, Button, Select, Tag, Space, Alert, Tooltip, Flex, Typography, Modal, Transfer } from 'antd';
import { FilterOutlined, ThunderboltOutlined, TeamOutlined, LinkOutlined, SyncOutlined } from '@ant-design/icons';
import * as SemesterService from '../services/semesterService';
import * as ClassSectionService from '../services/classSectionService';
import { fixAdminClasses } from '../services/classSectionService';
import * as ClassService from '../services/classService';
import * as TimetableService from '../services/timetableService';

const { Option } = Select;
const { Text } = Typography;

const ClassSectionManagement = () => {
    const { message } = App.useApp();
    const [sections, setSections] = useState([]);
    const [semesters, setSemesters] = useState([]);
    const [currentSemesterId, setCurrentSemesterId] = useState(null);
    const [loading, setLoading] = useState(false);
    const [typeFilter, setTypeFilter] = useState('ALL');
    const [assignStatusFilter, setAssignStatusFilter] = useState('ALL');

    // Lớp biên chế
    const [adminClasses, setAdminClasses] = useState([]);

    // Modal gán lớp biên chế (đơn lẻ)
    const [assignModalOpen, setAssignModalOpen] = useState(false);
    const [assigningSection, setAssigningSection] = useState(null);
    const [assignTargetKeys, setAssignTargetKeys] = useState([]);
    const [assignSaving, setAssignSaving] = useState(false);

    useEffect(() => {
        SemesterService.getSemesters().then(res => {
            setSemesters(res.data);
            const active = res.data?.find(s => s.isActive);
            if (active) setCurrentSemesterId(active.id);
        });
        ClassService.getClasses().then(res => setAdminClasses(res.data || [])).catch(() => { });
    }, []);

    const fetchSections = async () => {
        if (!currentSemesterId) return;
        setLoading(true);
        try {
            const res = await ClassSectionService.getClassSectionsBySemester(currentSemesterId);
            setSections(res.data || []);
        } catch {
            message.error('Lỗi tải danh sách lớp học phần');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchSections(); }, [currentSemesterId]);

    const handleGenerate = async (forceRegenerate = false) => {
        if (!currentSemesterId) { message.warning('Vui lòng chọn học kỳ'); return; }
        setLoading(true);
        try {
            const res = await ClassSectionService.generateClassSections(currentSemesterId, forceRegenerate);
            const count = res.data?.createdCount ?? 0;
            message.success(res.data?.message || `Đã sinh ${count} lớp học phần`);
            fetchSections();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi khi sinh lớp học phần');
        } finally {
            setLoading(false);
        }
    };

    const onGenerateClick = () => {
        if (!currentSemesterId) { message.warning('Vui lòng chọn học kỳ'); return; }
        if (sections.length > 0) {
            Modal.confirm({
                title: 'Đã có lớp học phần',
                content: `Học kỳ này đã có ${sections.length} lớp học phần. Xóa hết và sinh lại từ đầu? (TKB dự kiến cũng sẽ bị xóa.)`,
                okText: 'Xóa hết và sinh lại',
                okType: 'danger',
                cancelText: 'Hủy',
                onOk: () => handleGenerate(true),
            });
        } else {
            handleGenerate(false);
        }
    };

    const handleFixAdminClasses = async () => {
        if (!currentSemesterId) { message.warning('Vui lòng chọn học kỳ'); return; }
        setLoading(true);
        try {
            const res = await fixAdminClasses(currentSemesterId);
            const count = res.data?.fixedCount ?? 0;
            if (count === 0) {
                message.info('Không có lớp nào cần bổ sung lớp biên chế');
            } else {
                message.success(res.data?.message || `Đã cập nhật ${count} lớp học phần`);
            }
            fetchSections();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi khi bổ sung lớp biên chế');
        } finally {
            setLoading(false);
        }
    };

    // ── Gán đơn lẻ ────────────────────────────────────────────────────────────
    const handleOpenAssign = (section) => {
        setAssigningSection(section);
        const existingIds = (section.administrativeClasses || []).map(ac => String(ac.id));
        setAssignTargetKeys(existingIds);
        setAssignModalOpen(true);
    };

    const handleSaveAssign = async () => {
        if (!assigningSection) return;
        setAssignSaving(true);
        try {
            await TimetableService.assignAdminClassesToSection(
                assigningSection.id,
                assignTargetKeys.map(k => Number(k))
            );
            message.success('Đã cập nhật lớp biên chế');
            setAssignModalOpen(false);
            setAssigningSection(null);
            fetchSections();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi gán lớp biên chế');
        } finally {
            setAssignSaving(false);
        }
    };

    // ── Helpers ────────────────────────────────────────────────────────────────
    const filteredSections = sections.filter(s => {
        if (typeFilter !== 'ALL' && (s.sectionType || '').toUpperCase() !== typeFilter) return false;
        switch (assignStatusFilter) {
            case 'ASSIGNED':
                return !!s.lecturer;
            case 'UNASSIGNED':
                return !s.lecturer && !s.skipAssignment && !s.needsSupport;
            case 'SKIPPED':
                return !!s.skipAssignment;
            case 'NEED_SUPPORT':
                return !!s.needsSupport && !s.lecturer;
            default:
                return true;
        }
    });

    const adminClassTransferData = adminClasses.map(ac => ({
        key: String(ac.id),
        title: ac.code,
        description: `${ac.name}${ac.cohort ? ' · ' + ac.cohort : ''}`,
        studentCount: ac.studentCount,
    }));

    const acMap = Object.fromEntries(adminClasses.map(ac => [ac.id, ac]));

    // ── Columns ────────────────────────────────────────────────────────────────
    const columns = [
        {
            title: 'Mã lớp HP',
            dataIndex: 'code',
            width: 140,
            render: t => <span style={{ fontWeight: 600, color: '#005a8d' }}>{t}</span>,
        },
        {
            title: 'Loại',
            dataIndex: 'sectionType',
            width: 100,
            render: t => (
                <Tag color={t === 'LT' ? 'geekblue' : 'purple'} style={{ border: 'none' }}>
                    {t === 'LT' ? 'Lý thuyết' : 'Thực hành'}
                </Tag>
            ),
        },
        {
            title: 'Học phần',
            key: 'course',
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <span style={{ fontWeight: 500 }}>{r.courseOffering?.course?.code || '—'}</span>
                    <span style={{ fontSize: 12, color: '#666' }}>{r.courseOffering?.course?.name || '—'}</span>
                </Space>
            ),
        },
        {
            title: 'Khóa',
            key: 'cohort',
            width: 80,
            render: (_, r) => {
                const text = (r.courseOffering?.cohortRef && r.courseOffering.cohortRef.code)
                    || r.courseOffering?.cohort
                    || '—';
                return <Tag style={{ border: 'none', background: '#f5f5f5', color: '#666' }}>{text}</Tag>;
            },
        },
        {
            title: 'Khoa phụ trách',
            dataIndex: ['courseOffering', 'faculty', 'name'],
            render: t => t ? <Tag style={{ border: 'none', background: '#f5f5f5', color: '#666' }}>{t}</Tag> : '—',
        },
        {
            title: (
                <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    Lớp biên chế
                </span>
            ),
            key: 'adminClasses',
            width: 220,
            render: (_, r) => {
                const acs = r.administrativeClasses;
                return (
                    <Space wrap size={2}>
                        {acs && acs.length > 0
                            ? acs.map(ac => (
                                <Tag key={ac.id} color="cyan" style={{ border: 'none', fontSize: 11, padding: '0 6px' }}>
                                    {ac.code}
                                </Tag>
                            ))
                            : <Tag color="default" style={{ border: 'none', fontSize: 11, color: '#aaa' }}>Chưa gán</Tag>
                        }
                        <Tooltip title="Chỉnh sửa lớp biên chế">
                            <Button
                                type="text" size="small"
                                icon={<LinkOutlined />}
                                style={{ color: '#1890ff' }}
                                onClick={() => handleOpenAssign(r)}
                            />
                        </Tooltip>
                    </Space>
                );
            },
        },
        {
            title: 'Sĩ số dự kiến',
            key: 'studentCount',
            width: 120,
            align: 'right',
            render: (_, r) => {
                // Ưu tiên field expectedStudentCount từ backend (tính sẵn khi sinh lớp)
                const total = r.expectedStudentCount != null
                    ? r.expectedStudentCount
                    : (r.administrativeClasses || []).reduce((s, ac) => s + (acMap[ac.id]?.studentCount || ac.studentCount || 0), 0);
                if (total === 0) return <Text type="secondary" style={{ fontSize: 12 }}>—</Text>;
                const maxOk = r.sectionType === 'TH' ? 45 : 80;
                return (
                    <span style={{
                        fontWeight: 700, fontSize: 13,
                        color: total > maxOk ? '#cf1322' : '#389e0d',
                    }}>
                        {total} SV
                    </span>
                );
            },
        },
        {
            title: 'Trạng thái',
            key: 'assignStatus',
            width: 160,
            render: (_, r) => {
                if (r.skipAssignment) return <Tag color="orange">Bỏ qua</Tag>;
                if (r.lecturer) return <Tag color="green">Đã phân công</Tag>;
                if (r.needsSupport) return <Tag color="gold">Đã yêu cầu hỗ trợ</Tag>;
                return <Tag>Chưa phân công</Tag>;
            },
        },
    ];

    // ── Render ─────────────────────────────────────────────────────────────────
    return (
        <div style={{ width: '100%' }}>
            <div style={{
                display: 'flex', justifyContent: 'space-between',
                alignItems: 'center', gap: 16, marginBottom: 24, flexWrap: 'wrap',
            }}>
                <Space size={8} style={{ alignItems: 'center' }}>
                    <FilterOutlined style={{ color: '#666' }} />
                    <Select
                        variant="filled" style={{ minWidth: 200 }}
                        value={currentSemesterId} onChange={setCurrentSemesterId}
                        placeholder="Học kỳ" optionLabelProp="label"
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
                    <Select
                        variant="filled" style={{ minWidth: 140 }}
                        value={typeFilter} onChange={setTypeFilter} placeholder="Loại"
                    >
                        <Option value="ALL">Tất cả</Option>
                        <Option value="LT">Lý thuyết</Option>
                        <Option value="TH">Thực hành</Option>
                    </Select>
                    <Select
                        variant="filled" style={{ minWidth: 180 }}
                        value={assignStatusFilter} onChange={setAssignStatusFilter}
                        placeholder="Trạng thái phân công"
                    >
                        <Option value="ALL">Tất cả trạng thái</Option>
                        <Option value="ASSIGNED">Đã phân công</Option>
                        <Option value="UNASSIGNED">Chưa phân công</Option>
                        <Option value="SKIPPED">Bỏ qua</Option>
                        <Option value="NEED_SUPPORT">Đã yêu cầu hỗ trợ</Option>
                    </Select>
                </Space>

                <Space size={8}>
                    <Tooltip title="Bổ sung lớp biên chế cho các lớp HP còn thiếu hoặc đang vượt sĩ số tối đa. Có kiểm tra CTĐT và khóa học.">
                        <Button
                            icon={<SyncOutlined />}
                            onClick={handleFixAdminClasses}
                            loading={loading}
                            disabled={!currentSemesterId || sections.length === 0}
                        >
                            Bổ sung lớp biên chế
                        </Button>
                    </Tooltip>
                    <Tooltip title="Sinh các lớp học phần từ danh sách học phần đã được Khoa/Viện xác nhận (Module 1). Lớp biên chế được gán tự động theo quy tắc chia đều. Nếu đã có lớp, sẽ hỏi xác nhận xóa hết và sinh lại.">
                        <Button
                            type="primary"
                            icon={<ThunderboltOutlined />}
                            onClick={onGenerateClick}
                            loading={loading}
                            disabled={!currentSemesterId}
                        >
                            Sinh lớp học phần
                        </Button>
                    </Tooltip>
                </Space>
            </div>

            <Alert
                title="Module 2: Quản lý Lớp học phần"
                description="Hệ thống sinh tự động các Lớp học phần từ danh sách học phần đã được Khoa/Viện xác nhận. Lớp biên chế chia theo quy mô: LT 40–80 SV/lớp, TH 20–45 SV/lớp (lọc theo Khoa và CTĐT). Nếu sau khi sinh còn lớp thiếu BC hoặc vượt sĩ số, dùng 'Bổ sung lớp biên chế' để hệ thống tự phân bổ lại."
                type="info" showIcon
                style={{ marginBottom: 20, border: 'none', background: '#e6f7ff' }}
            />

            <Table
                dataSource={filteredSections}
                columns={columns}
                rowKey="id"
                loading={loading}
                size="middle"
                pagination={{ pageSize: 10, placement: 'bottomRight', showTotal: t => `Tổng ${t} lớp` }}
                scroll={{ x: 1000 }}
            />

            {/* ── Modal chỉnh sửa lớp biên chế (đơn lẻ) ──────────────────── */}
            <Modal
                title={
                    <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        Chỉnh sửa lớp biên chế — {assigningSection?.code}
                    </span>
                }
                open={assignModalOpen}
                onOk={handleSaveAssign}
                onCancel={() => { setAssignModalOpen(false); setAssigningSection(null); }}
                okText="Lưu"
                cancelText="Hủy"
                confirmLoading={assignSaving}
                width={700}
                centered
            >
                {assigningSection && (() => {
                    const expectedStudents = assignTargetKeys.reduce(
                        (s, k) => s + (acMap[Number(k)]?.studentCount || 0), 0
                    );
                    return (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                            {/* Info bar */}
                            <div style={{
                                display: 'flex', alignItems: 'center', gap: 10,
                                padding: '8px 14px', borderRadius: 8,
                                background: 'linear-gradient(135deg,#f0f5ff,#e6f7ff)',
                                border: '1px solid #adc6ff',
                            }}>
                                <Tag
                                    color={assigningSection.sectionType === 'LT' ? 'geekblue' : 'purple'}
                                    style={{ border: 'none', fontWeight: 600 }}
                                >
                                    {assigningSection.sectionType === 'LT' ? 'Lý thuyết' : 'Thực hành'}
                                </Tag>
                                <Text style={{ fontWeight: 700, color: '#003a8c' }}>{assigningSection.code}</Text>
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                    — {assigningSection.courseOffering?.course?.name}
                                </Text>
                                {expectedStudents > 0 && (
                                    <Tag
                                        color={expectedStudents > (assigningSection?.sectionType === 'TH' ? 45 : 80) ? 'red' : 'green'}
                                        style={{ marginLeft: 'auto', border: 'none', fontWeight: 700 }}
                                    >
                                        {expectedStudents} SV dự kiến
                                    </Tag>
                                )}
                            </div>

                            <Transfer
                                dataSource={adminClassTransferData}
                                titles={['Chưa gán', 'Đã gán']}
                                targetKeys={assignTargetKeys}
                                onChange={nextKeys => setAssignTargetKeys(nextKeys)}
                                render={item => (
                                    <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                        <Tag color="cyan" style={{ border: 'none', fontSize: 10, padding: '0 5px', margin: 0 }}>{item.title}</Tag>
                                        <span style={{ fontSize: 11, color: '#666' }}>{item.description}</span>
                                        {item.studentCount != null && (
                                            <Text type="secondary" style={{ fontSize: 10, marginLeft: 'auto' }}>{item.studentCount} SV</Text>
                                        )}
                                    </span>
                                )}
                                showSearch
                                filterOption={(input, item) =>
                                    item.title.toLowerCase().includes(input.toLowerCase()) ||
                                    item.description.toLowerCase().includes(input.toLowerCase())
                                }
                                listStyle={{ width: 290, height: 300 }}
                                locale={{
                                    itemUnit: 'lớp', itemsUnit: 'lớp',
                                    searchPlaceholder: 'Tìm lớp biên chế...',
                                    notFoundContent: 'Không có dữ liệu',
                                }}
                            />

                            {expectedStudents > (assigningSection?.sectionType === 'TH' ? 45 : 80) && (
                                <Alert
                                    message={assigningSection?.sectionType === 'TH'
                                        ? 'Sĩ số vượt 45 — phòng thực hành có thể không đủ chỗ'
                                        : 'Sĩ số vượt 80 — cân nhắc chọn phòng lớn khi xếp TKB'}
                                    type="warning" showIcon
                                    style={{ border: 'none', padding: '4px 12px', fontSize: 12 }}
                                />
                            )}
                        </div>
                    );
                })()}
            </Modal>
        </div>
    );
};

export default ClassSectionManagement;
