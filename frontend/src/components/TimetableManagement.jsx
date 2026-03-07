import React, { useState, useEffect, useRef } from 'react';
import { App, Table, Select, Tag, Space, Alert, Button, Modal, Tooltip, Flex, Typography, Progress, Collapse } from 'antd';
import { CalendarOutlined, ThunderboltOutlined, CheckCircleOutlined, EditOutlined, DeleteOutlined, BulbOutlined, LoadingOutlined, FileExcelOutlined, TeamOutlined, WarningOutlined } from '@ant-design/icons';
import * as SemesterService from '../services/semesterService';
import * as TimetableService from '../services/timetableService';
import * as RoomService from '../services/roomService';
import * as TimeService from '../services/timeService';
import * as ClassService from '../services/classService';

const { Option } = Select;
const { Text } = Typography;

const DAY_NAMES = { 2: 'Thứ 2', 3: 'Thứ 3', 4: 'Thứ 4', 5: 'Thứ 5', 6: 'Thứ 6', 7: 'Thứ 7' };
const DAY_COLORS = { 2: 'blue', 3: 'cyan', 4: 'geekblue', 5: 'purple', 6: 'magenta', 7: 'orange' };
const GENERATION_STATUSES = [
    'Đang khởi tạo giải pháp greedy...',
    'Đang đánh giá fitness...',
    'Đang tìm láng giềng (move/swap)...',
    'Đang áp dụng hàm chấp nhận...',
    'Đang làm nguội (cooling)...',
    'Đang tối ưu hóa giải pháp...',
];

const STYLES = {
    leftPanel: { flex: 1, padding: '20px 20px 20px 24px', borderRight: '1px solid #eef0f3', display: 'flex', flexDirection: 'column', gap: 14 },
    rightPanel: { flex: 1, padding: '20px 24px 20px 20px', display: 'flex', flexDirection: 'column', gap: 12, background: '#fafbfc' },
    infoBox: { padding: '10px 14px', background: 'linear-gradient(135deg, #f0f5ff 0%, #e6f7ff 100%)', borderRadius: 10, border: '1px solid #adc6ff' },
    suggestionsBox: { padding: '10px 12px', background: '#fffbe6', borderRadius: 8, border: '1px solid #ffe58f' },
    fieldLabel: { fontSize: 12, fontWeight: 600, color: '#555', marginBottom: 5 },
    sectionLabel: { fontSize: 11, fontWeight: 700, color: '#999', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 10 },
    adminClassCard: { padding: '12px 14px', background: '#fff', borderRadius: 10, border: '1px solid #eef0f3', flex: 1 },
    acItem: { display: 'flex', alignItems: 'center', gap: 8, padding: '6px 10px', borderRadius: 6, background: '#f0f5ff', border: '1px solid #d6e4ff' },
};

const getApiError = (e, fallback) => e?.response?.data?.message || fallback;

const SA_EVALUATION_STORAGE_KEY = 'timetablex_sa_evaluation';

function loadSaEvaluationFromStorage() {
    try {
        const raw = localStorage.getItem(SA_EVALUATION_STORAGE_KEY);
        if (!raw) return null;
        const data = JSON.parse(raw);
        if (data && typeof data.semesterName === 'string') return data;
    } catch (_) { /* ignore */ }
    return null;
}

function saveSaEvaluationToStorage(data) {
    try {
        if (data) localStorage.setItem(SA_EVALUATION_STORAGE_KEY, JSON.stringify(data));
        else localStorage.removeItem(SA_EVALUATION_STORAGE_KEY);
    } catch (_) { /* ignore */ }
}

const GA_EVALUATION_STORAGE_KEY = 'timetablex_ga_evaluation';
function saveGaEvaluationToStorage(data) {
    try {
        if (data) localStorage.setItem(GA_EVALUATION_STORAGE_KEY, JSON.stringify(data));
        else localStorage.removeItem(GA_EVALUATION_STORAGE_KEY);
    } catch (_) { /* ignore */ }
}

/**
 * Module 4: Xếp Thời khóa biểu (Core Engine)
 * P.ĐT kích hoạt thuật toán xếp TKB tự động hoặc chỉnh sửa thủ công.
 * Hỗ trợ lọc TKB theo lớp biên chế và gán lớp biên chế cho lớp học phần.
 */
const TimetableManagement = () => {
    const { message: messageApi, modal } = App.useApp();
    const [semesters, setSemesters] = useState([]);
    const [currentSemesterId, setCurrentSemesterId] = useState(null);
    const [timetableEntries, setTimetableEntries] = useState([]);
    const [rooms, setRooms] = useState([]);
    const [shifts, setShifts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [generating, setGenerating] = useState(false);
    const [generationProgress, setGenerationProgress] = useState(0);
    const [generationStatus, setGenerationStatus] = useState('');
    const [editModalOpen, setEditModalOpen] = useState(false);
    const [selectedEntry, setSelectedEntry] = useState(null);
    const [editForm, setEditForm] = useState({ roomId: null, shiftId: null, dayOfWeek: null });
    const [editAssignTargetKeys, setEditAssignTargetKeys] = useState([]);
    const [suggestions, setSuggestions] = useState([]);
    const [generateModalOpen, setGenerateModalOpen] = useState(false);
    const [selectedAlgorithm, setSelectedAlgorithm] = useState('SA'); // 'SA' | 'GA'
    const [exporting, setExporting] = useState(false);
    const [evaluationModalOpen, setEvaluationModalOpen] = useState(false);
    const [saEvaluation, setSaEvaluation] = useState(() => loadSaEvaluationFromStorage());
    const [gaEvaluation, setGaEvaluation] = useState(() => {
        try {
            const raw = localStorage.getItem('timetablex_ga_evaluation');
            if (!raw) return null;
            const data = JSON.parse(raw);
            if (data && typeof data.semesterName === 'string') return data;
        } catch (_) { /* ignore */ }
        return null;
    });
    const progressIntervalRef = useRef(null);

    // Lọc theo lớp biên chế (chỉ dùng để xem TKB)
    const [adminClasses, setAdminClasses] = useState([]);
    const [selectedAdminClassId, setSelectedAdminClassId] = useState(null);

    useEffect(() => {
        SemesterService.getSemesters().then(res => {
            setSemesters(res.data || []);
            const active = (res.data || []).find(s => s.isActive);
            if (active) setCurrentSemesterId(active.id);
        });
        RoomService.getRooms().then(res => setRooms(res.data || []));
        TimeService.getShifts().then(res => setShifts(res.data || [])).catch(() => {});
        ClassService.getClasses().then(res => setAdminClasses(res.data || [])).catch(() => {});
    }, []);

    useEffect(() => {
        if (currentSemesterId) {
            fetchTimetable();
        }
    }, [currentSemesterId, selectedAdminClassId]);

    const fetchTimetable = async () => {
        if (!currentSemesterId) return;
        setLoading(true);
        try {
            const res = selectedAdminClassId
                ? await TimetableService.getTimetableByAdminClass(currentSemesterId, selectedAdminClassId)
                : await TimetableService.getTimetable(currentSemesterId);
            setTimetableEntries(res.data || []);
        } catch {
            messageApi.error('Lỗi tải TKB');
            setTimetableEntries([]);
        } finally {
            setLoading(false);
        }
    };

    const handleOpenGenerateModal = () => {
        if (!currentSemesterId) {
            messageApi.warning('Vui lòng chọn học kỳ');
            return;
        }
        setGenerateModalOpen(true);
    };

    const handleCloseGenerateModal = () => {
        if (!generating) {
            setGenerateModalOpen(false);
            setGenerationProgress(0);
            setGenerationStatus('');
        }
    };

    const handleStartGenerate = async () => {
        const startTime = Date.now();
        setGenerating(true);
        setGenerationProgress(0);
        setGenerationStatus('Đang khởi tạo thuật toán...');

        let simulatedProgress = 0;
        progressIntervalRef.current = setInterval(() => {
            simulatedProgress += Math.random() * 3;
            if (simulatedProgress < 95) {
                setGenerationProgress(Math.min(95, Math.round(simulatedProgress)));
                setGenerationStatus(GENERATION_STATUSES[Math.floor(Math.random() * GENERATION_STATUSES.length)]);
            }
        }, 200);

        try {
            const res = await TimetableService.generateTimetable(currentSemesterId, selectedAlgorithm);

            if (progressIntervalRef.current) {
                clearInterval(progressIntervalRef.current);
                progressIntervalRef.current = null;
            }
            setGenerationProgress(100);
            setGenerationStatus('Hoàn thành!');

            const data = res.data || {};
            const assigned = data.assignedCount || 0;
            const conflicts = data.conflictCount || 0;
            const usedAlgo = data.algorithm === 'GA' ? 'GA' : 'SA';

            const semesterName = semesters.find(s => s.id === currentSemesterId)?.name || `Học kỳ ${currentSemesterId}`;
            const runtimeMs = Date.now() - startTime;

            const evaluation = {
                semesterId: currentSemesterId,
                semesterName,
                algorithm: usedAlgo,
                assignedCount: assigned,
                conflictCount: conflicts,
                unscheduledSections: data.unscheduledSections ?? 0,
                algorithmConflicts: typeof data.algorithmConflicts === 'number' ? data.algorithmConflicts : null,
                fitness: data.fitness,
                overloadWarnings: data.overloadWarnings || [],
                conflicts: data.conflicts || [],
                runtimeMs,
            };
            if (usedAlgo === 'GA') {
                setGaEvaluation(evaluation);
                saveGaEvaluationToStorage(evaluation);
            } else {
                setSaEvaluation(evaluation);
                saveSaEvaluationToStorage(evaluation);
            }

            await new Promise(resolve => setTimeout(resolve, 500));

            if (conflicts > 0 && data.conflicts) {
                modal.warning({
                    title: 'Xếp TKB hoàn tất với một số xung đột',
                    width: 600,
                    content: (
                        <div>
                            <p>Đã xếp được <strong>{assigned}</strong> buổi học.</p>
                            <p>Có <strong>{conflicts}</strong> xung đột không thể xếp tự động:</p>
                            <ul style={{ maxHeight: 300, overflowY: 'auto' }}>
                                {data.conflicts.map((c, i) => (
                                    <li key={i} style={{ fontSize: 12, marginBottom: 4 }}>{c}</li>
                                ))}
                            </ul>
                        </div>
                    ),
                });
            } else {
                messageApi.success(`Đã xếp ${assigned} buổi học thành công (${usedAlgo === 'GA' ? 'Genetic Algorithm' : 'Simulated Annealing'})`);
            }
            fetchTimetable();
            setGenerateModalOpen(false);
        } catch (e) {
            if (progressIntervalRef.current) {
                clearInterval(progressIntervalRef.current);
                progressIntervalRef.current = null;
            }
            const errMsg = e?.code === 'ECONNABORTED'
                ? 'Thuật toán chạy quá lâu. Vui lòng thử lại.'
                : (e?.response?.data?.message || 'Lỗi xếp TKB');
            messageApi.error(errMsg);
        } finally {
            setGenerating(false);
            setGenerationProgress(0);
            setGenerationStatus('');
        }
    };

    useEffect(() => {
        return () => {
            if (progressIntervalRef.current) {
                clearInterval(progressIntervalRef.current);
            }
        };
    }, []);

    const handleConfirm = async () => {
        if (!currentSemesterId) return;
        modal.confirm({
            title: (
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 20 }} />
                    Xác nhận Thời khóa biểu?
                </span>
            ),
            content: (
                <div style={{ padding: '8px 0' }}>
                    <p style={{ marginBottom: 8, fontSize: 14 }}>
                        Tất cả các buổi học dự kiến (DRAFT) sẽ được chuyển sang trạng thái <strong>Đã xác nhận</strong>.
                    </p>
                    <p style={{ margin: 0, color: '#fa8c16', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}>
                        <WarningOutlined /> Sau khi xác nhận, bạn sẽ không thể chỉnh sửa hoặc xóa các buổi học nữa.
                    </p>
                </div>
            ),
            okText: 'Xác nhận',
            cancelText: 'Hủy',
            okButtonProps: { type: 'primary', danger: false },
            width: 440,
            async onOk() {
                try {
                    await TimetableService.confirmTimetable(currentSemesterId);
                    messageApi.success('Đã xác nhận TKB thành công');
                    fetchTimetable();
                } catch (e) {
                    messageApi.error(getApiError(e, 'Lỗi xác nhận TKB'));
                }
            },
        });
    };

    const handleOpenEdit = async (entry) => {
        setSelectedEntry(entry);
        setEditForm({
            roomId: entry.room?.id,
            shiftId: entry.shift?.id,
            dayOfWeek: entry.dayOfWeek,
        });
        // Khởi tạo lớp biên chế đã gán
        const existing = (entry.classSection?.administrativeClasses || []).map(ac => String(ac.id));
        setEditAssignTargetKeys(existing);
        setEditModalOpen(true);
        setSuggestions([]);
        if (entry.status === 'DRAFT') {
            try {
                const res = await TimetableService.getSuggestions(entry.id);
                setSuggestions(res.data || []);
            } catch {
                setSuggestions([]);
            }
        }
    };

    const handleSaveEdit = async () => {
        if (!selectedEntry) return;
        try {
            // Lưu thay đổi phòng/ca/thứ
            await TimetableService.updateTimetableEntry(selectedEntry.id, editForm);
            // Lưu lớp biên chế nếu có thay đổi
            const sectionId = selectedEntry.classSection?.id;
            if (sectionId !== undefined) {
                await TimetableService.assignAdminClassesToSection(
                    sectionId,
                    editAssignTargetKeys.map(k => Number(k))
                );
            }
            messageApi.success('Đã cập nhật TKB và lớp biên chế');
            setEditModalOpen(false);
            setSelectedEntry(null);
            fetchTimetable();
        } catch (e) {
            messageApi.error(getApiError(e, 'Lỗi cập nhật TKB'));
        }
    };

    const handleDelete = async (entry) => {
        modal.confirm({
            title: 'Xóa buổi học này?',
            content: `Xóa buổi học của lớp ${entry.classSection?.code}?`,
            okText: 'Xóa',
            okType: 'danger',
            cancelText: 'Hủy',
            async onOk() {
                try {
                    await TimetableService.deleteTimetableEntry(entry.id);
                    messageApi.success('Đã xóa buổi học');
                    fetchTimetable();
                } catch (e) {
                    messageApi.error(getApiError(e, 'Lỗi xóa buổi học'));
                }
            },
        });
    };

    const handleExport = async () => {
        if (!currentSemesterId) return;
        setExporting(true);
        try {
            const res = await TimetableService.exportTimetable(currentSemesterId);
            const semesterName = semesters.find(s => s.id === currentSemesterId)?.name || `HocKy_${currentSemesterId}`;
            const safeName = semesterName.replace(/[^a-zA-Z0-9_\-À-ỹ ]/g, '').trim().replace(/\s+/g, '_');
            const url = URL.createObjectURL(new Blob([res.data], {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            }));
            const link = document.createElement('a');
            link.href = url;
            link.download = `TKB_${safeName}.xlsx`;
            link.click();
            URL.revokeObjectURL(url);
            messageApi.success('Xuất file Excel thành công');
        } catch {
            messageApi.error('Không thể xuất file Excel. Vui lòng thử lại.');
        } finally {
            setExporting(false);
        }
    };

    const hasDraftEntries = timetableEntries.some(e => e.status === 'DRAFT');
    const canExport = currentSemesterId && timetableEntries.length > 0;
    const selectedAdminClass = adminClasses.find(a => a.id === selectedAdminClassId);
    const sortedEntries = [...timetableEntries].sort((a, b) => {
        if (a.dayOfWeek !== b.dayOfWeek) return a.dayOfWeek - b.dayOfWeek;
        const aP = a.startPeriod ?? a.shift?.startPeriod ?? a.timeSlot?.periodIndex ?? 0;
        const bP = b.startPeriod ?? b.shift?.startPeriod ?? b.timeSlot?.periodIndex ?? 0;
        return aP - bP;
    });

    const columns = [
        {
            title: 'Lớp HP',
            dataIndex: ['classSection', 'code'],
            key: 'section',
            width: 130,
            fixed: 'left',
            render: t => <span style={{ fontWeight: 600, color: '#005a8d' }}>{t}</span>,
        },
        {
            title: 'Môn học',
            key: 'course',
            width: 200,
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <span style={{ fontWeight: 500 }}>
                        {r.classSection?.courseOffering?.course?.name || '—'}
                    </span>
                    <span style={{ fontSize: 11, color: '#888' }}>
                        {r.classSection?.courseOffering?.course?.code}
                        {r.classSection?.sectionType && (
                            <Tag
                                color={r.classSection.sectionType === 'LT' ? 'geekblue' : 'purple'}
                                style={{ border: 'none', fontSize: 10, marginLeft: 4, padding: '0 4px' }}
                            >
                                {r.classSection.sectionType === 'LT' ? 'Lý thuyết' : 'Thực hành'}
                            </Tag>
                        )}
                    </span>
                </Space>
            ),
        },
        {
            title: (
                <Space size={4}>
                    <span>Lớp biên chế</span>
                </Space>
            ),
            key: 'adminClasses',
            width: 170,
            render: (_, r) => {
                const acs = r.classSection?.administrativeClasses;
                if (!acs || acs.length === 0) {
                    return <Text type="secondary" style={{ fontSize: 11 }}>— Chưa gán</Text>;
                }
                return (
                    <Space wrap size={2}>
                        {acs.map(ac => (
                            <Tag key={ac.id} color="cyan" style={{ border: 'none', fontSize: 11, padding: '0 6px', margin: 0 }}>
                                {ac.code}
                            </Tag>
                        ))}
                    </Space>
                );
            },
        },
        {
            title: 'Thứ',
            dataIndex: 'dayOfWeek',
            key: 'day',
            width: 80,
            align: 'center',
            render: d => (
                <Tag color={DAY_COLORS[d] || 'default'} style={{ border: 'none', fontWeight: 600 }}>
                    {DAY_NAMES[d] || `Thứ ${d}`}
                </Tag>
            ),
        },
        {
            title: 'Ca học / Tiết',
            key: 'slot',
            width: 160,
            render: (_, r) => {
                if (r.shift) {
                    const start = r.startPeriod ?? r.shift.startPeriod;
                    const end = r.endPeriod ?? r.shift.endPeriod;
                    return (
                        <Space direction="vertical" size={0}>
                            <Tag color="volcano" style={{ border: 'none', fontWeight: 600 }}>{r.shift.name}</Tag>
                            <span style={{ fontSize: 11, color: '#888' }}>Tiết {start} – {end}</span>
                        </Space>
                    );
                }
                if (r.timeSlot) return <Tag style={{ border: 'none' }}>{r.timeSlot.name}</Tag>;
                return <span style={{ color: '#ccc' }}>—</span>;
            },
        },
        {
            title: 'Phòng',
            key: 'room',
            width: 120,
            render: (_, r) => r.room ? (
                <Space direction="vertical" size={0}>
                    <span style={{ fontWeight: 500 }}>{r.room.name}</span>
                    <Tag style={{ border: 'none', background: '#f5f5f5', color: '#666', fontSize: 10 }}>
                        {r.room.type}
                    </Tag>
                </Space>
            ) : <span style={{ color: '#ccc' }}>—</span>,
        },
        {
            title: 'Giảng viên',
            key: 'lecturer',
            width: 160,
            render: (_, r) => {
                const lec = r.classSection?.lecturer;
                return lec
                    ? <span style={{ fontWeight: 500 }}>{lec.name}</span>
                    : <Tag color="warning" style={{ border: 'none' }}>Chưa phân công</Tag>;
            },
        },
        {
            title: 'Trạng thái',
            dataIndex: 'status',
            key: 'status',
            width: 120,
            align: 'center',
            render: s => (
                <Tag color={s === 'DRAFT' ? 'orange' : 'green'} style={{ border: 'none' }}>
                    {s === 'DRAFT' ? 'Dự kiến' : 'Đã xác nhận'}
                </Tag>
            ),
        },
        {
            title: 'Thao tác',
            key: 'actions',
            width: 90,
            fixed: 'right',
            align: 'right',
            render: (_, record) => {
                if (record.status !== 'DRAFT') return <span style={{ color: '#ccc' }}>—</span>;
                return (
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 4 }}>
                        <Tooltip title="Chỉnh sửa">
                            <Button type="text" size="small" icon={<EditOutlined />} style={{ color: '#666' }} onClick={() => handleOpenEdit(record)} />
                        </Tooltip>
                        <Tooltip title="Xóa">
                            <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)} />
                        </Tooltip>
                    </div>
                );
            },
        },
    ];

    return (
        <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 12 }}>
                <Space wrap size={8}>
                    <Select
                        variant="filled"
                        style={{ minWidth: 200 }}
                        value={currentSemesterId}
                        onChange={v => { setCurrentSemesterId(v); setSelectedAdminClassId(null); }}
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

                    {/* Lọc theo lớp biên chế */}
                    <Select
                        variant="filled"
                        style={{ minWidth: 200 }}
                        value={selectedAdminClassId}
                        onChange={setSelectedAdminClassId}
                        placeholder="Lọc theo lớp biên chế..."
                        allowClear
                        showSearch
                        optionFilterProp="label"
                        disabled={!currentSemesterId}
                        suffixIcon={<TeamOutlined style={{ color: selectedAdminClassId ? '#1890ff' : undefined }} />}
                    >
                        {adminClasses.map(ac => (
                            <Option key={ac.id} value={ac.id} label={`${ac.code} - ${ac.name}`}>
                                <Space size={6}>
                                    <Tag color="cyan" style={{ border: 'none', fontSize: 11 }}>{ac.code}</Tag>
                                    <span>{ac.name}</span>
                                    {ac.cohort && <Text type="secondary" style={{ fontSize: 11 }}>{ac.cohort}</Text>}
                                </Space>
                            </Option>
                        ))}
                    </Select>
                </Space>
                <Space size={8} wrap>
                    <Button
                        type="primary"
                        icon={<ThunderboltOutlined />}
                        onClick={handleOpenGenerateModal}
                        loading={generating}
                        disabled={!currentSemesterId}
                        size="middle"
                    >
                        Xếp TKB tự động
                    </Button>
                    {hasDraftEntries && (
                        <Button
                            type="default"
                            icon={<CheckCircleOutlined />}
                            onClick={handleConfirm}
                            disabled={!currentSemesterId}
                            size="middle"
                        >
                            Xác nhận TKB
                        </Button>
                    )}

                    <Button
                        icon={<FileExcelOutlined />}
                        onClick={handleExport}
                        loading={exporting}
                        disabled={!canExport}
                        size="middle"
                        style={canExport ? { color: '#389e0d', borderColor: '#b7eb8f', background: '#f6ffed' } : undefined}
                    >
                        Xuất Excel
                    </Button>
                    <Button
                        size="middle"
                        onClick={() => setEvaluationModalOpen(true)}
                    >
                        Xem đánh giá SA vs GA
                    </Button>
                </Space>
            </div>

            {selectedAdminClass && (
                <Alert
                    message={<span>Đang xem TKB của lớp biên chế: <strong>{selectedAdminClass.code} {selectedAdminClass.name}</strong></span>}
                    type="info"
                    showIcon
                    icon={<TeamOutlined />}
                    closable
                    onClose={() => setSelectedAdminClassId(null)}
                    style={{ marginBottom: 12, border: 'none', background: '#e6f7ff' }}
                />
            )}

            <Alert
                title="Module 4: Xếp Thời khóa biểu"
                description="Kích hoạt thuật toán xếp TKB tự động dựa trên dữ liệu phân công giảng viên, phòng học và khung giờ. Sau khi xếp có thể chỉnh sửa thủ công. Thời gian xử lý: 03 tuần."
                type="info"
                showIcon
                icon={<CalendarOutlined />}
                style={{ marginBottom: 20, border: 'none', background: '#e6f7ff' }}
            />

            {!currentSemesterId ? (
                <Alert title="Chọn học kỳ để xem và xếp TKB" type="warning" showIcon />
            ) : (
                <Table
                    dataSource={sortedEntries}
                    columns={columns}
                    rowKey="id"
                    loading={loading}
                    size="middle"
                    pagination={{ pageSize: 10, showSizeChanger: true, placement: 'bottomRight', showTotal: (t) => `Tổng ${t} buổi học` }}
                    scroll={{ x: 1200 }}
                />
            )}

            {/* Modal Đánh giá thuật toán SA vs GA */}
            <Modal
                title={
                    <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <BulbOutlined style={{ color: '#faad14', fontSize: 20 }} />
                        <span>So sánh SA và GA</span>
                    </span>
                }
                open={evaluationModalOpen}
                onCancel={() => setEvaluationModalOpen(false)}
                footer={null}
                centered
                width={720}
                styles={{ body: { paddingTop: 4 }, header: { borderBottom: '1px solid #f0f0f0', paddingBottom: 12 } }}
            >
                <div style={{ maxHeight: 560, overflowY: 'auto', paddingRight: 4 }}>
                    <p style={{ margin: '0 0 20px 0', fontSize: 13, color: '#595959', lineHeight: 1.6 }}>
                        Hai thuật toán <strong>Simulated Annealing</strong> và <strong>Genetic Algorithm</strong> cho kết quả tương đương.
                        Chạy từng thuật toán trong &quot;Xếp TKB tự động&quot;, kết quả được lưu lại để so sánh bên dưới.
                    </p>

                    {/* Bảng so sánh khi có cả SA và GA */}
                    {saEvaluation && gaEvaluation && (() => {
                        const sameSemester = saEvaluation.semesterId === gaEvaluation.semesterId;
                        const renderCell = (saVal, gaVal, higherBetter) => {
                            const saNum = typeof saVal === 'number' ? saVal : null;
                            const gaNum = typeof gaVal === 'number' ? gaVal : null;
                            const bothNum = saNum != null && gaNum != null;
                            const saBetter = bothNum && (higherBetter ? saNum > gaNum : saNum < gaNum);
                            const gaBetter = bothNum && (higherBetter ? gaNum > saNum : gaNum < saNum);
                            const Badge = ({ show }) => show ? <span style={{ marginLeft: 6, fontSize: 10, color: '#52c41a', fontWeight: 500 }}>✓ tốt hơn</span> : null;
                            return {
                                sa: saNum != null ? saNum : '—',
                                ga: gaNum != null ? gaNum : '—',
                                saBadge: <Badge show={saBetter} />,
                                gaBadge: <Badge show={gaBetter} />,
                            };
                        };
                        const assigned = renderCell(saEvaluation.assignedCount, gaEvaluation.assignedCount, true);
                        const conflicts = renderCell(saEvaluation.conflictCount, gaEvaluation.conflictCount, false);
                        const algoConflicts = renderCell(saEvaluation.algorithmConflicts, gaEvaluation.algorithmConflicts, false);
                        const unscheduled = renderCell(saEvaluation.unscheduledSections, gaEvaluation.unscheduledSections, false);
                        const fitness = renderCell(saEvaluation.fitness, gaEvaluation.fitness, true);
                        return (
                            <div style={{
                                marginBottom: 20,
                                padding: 16,
                                borderRadius: 12,
                                background: 'linear-gradient(180deg, #fafafa 0%, #f5f5f5 100%)',
                                border: '1px solid #e8e8e8',
                                boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
                            }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                                    <span style={{ fontWeight: 600, fontSize: 14, color: '#262626' }}>So sánh trực tiếp</span>
                                    {sameSemester ? (
                                        <Tag color="blue" style={{ margin: 0 }}>Cùng học kỳ</Tag>
                                    ) : (
                                        <Tag color="default" style={{ margin: 0 }}>Khác học kỳ</Tag>
                                    )}
                                </div>
                                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                                    <thead>
                                        <tr>
                                            <th style={{ padding: '10px 12px', textAlign: 'left', background: 'rgba(0,0,0,0.04)', borderRadius: '8px 0 0 0', fontWeight: 600, color: '#595959' }}>Chỉ số</th>
                                            <th style={{ padding: '10px 12px', textAlign: 'center', background: 'rgba(22, 119, 255, 0.08)', fontWeight: 600, color: '#1677ff' }}>SA</th>
                                            <th style={{ padding: '10px 12px', textAlign: 'center', background: 'rgba(0, 185, 164, 0.08)', fontWeight: 600, color: '#00b9a4' }}>GA</th>
                                            <th style={{ padding: '10px 12px', textAlign: 'left', background: 'rgba(0,0,0,0.04)', color: '#8c8c8c', fontSize: 11, fontWeight: 500 }}>Hướng</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {(() => {
                                            const rowData = [
                                                { label: 'Buổi đã xếp', sa: assigned.sa, ga: assigned.ga, saB: assigned.saBadge, gaB: assigned.gaBadge, hint: 'Càng nhiều càng tốt' },
                                                { label: 'Xung đột khi lưu', sa: conflicts.sa, ga: conflicts.ga, saB: conflicts.saBadge, gaB: conflicts.gaBadge, hint: 'Càng ít càng tốt' },
                                                (typeof saEvaluation.algorithmConflicts === 'number' || typeof gaEvaluation.algorithmConflicts === 'number') && { label: 'Xung đột trong thuật toán', sa: algoConflicts.sa, ga: algoConflicts.ga, saB: algoConflicts.saBadge, gaB: algoConflicts.gaBadge, hint: 'Càng ít càng tốt' },
                                                { label: 'Lớp bị ảnh hưởng', sa: unscheduled.sa, ga: unscheduled.ga, saB: unscheduled.saBadge, gaB: unscheduled.gaBadge, hint: 'Càng ít càng tốt' },
                                                { label: 'Fitness', sa: fitness.sa, ga: fitness.ga, saB: fitness.saBadge, gaB: fitness.gaBadge, hint: 'Càng cao càng tốt' },
                                                { label: 'Thời gian chạy', sa: `${(saEvaluation.runtimeMs / 1000).toFixed(1)}s`, ga: `${(gaEvaluation.runtimeMs / 1000).toFixed(1)}s`, saB: null, gaB: null, hint: '—' },
                                            ];
                                            const rows = rowData.filter(Boolean);
                                            return rows.map((row, idx) => (
                                                <tr key={idx} style={{ borderBottom: idx < rows.length - 1 ? '1px solid #f0f0f0' : 'none' }}>
                                                    <td style={{ padding: '10px 12px', color: '#595959' }}>{row.label}</td>
                                                    <td style={{ padding: '10px 12px', textAlign: 'center', background: 'rgba(22, 119, 255, 0.03)' }}><strong style={{ color: '#262626' }}>{row.sa}</strong>{row.saB}</td>
                                                    <td style={{ padding: '10px 12px', textAlign: 'center', background: 'rgba(0, 185, 164, 0.03)' }}><strong style={{ color: '#262626' }}>{row.ga}</strong>{row.gaB}</td>
                                                    <td style={{ padding: '10px 12px', color: '#8c8c8c', fontSize: 11 }}>{row.hint}</td>
                                                </tr>
                                            ));
                                        })()}
                                    </tbody>
                                </table>
                            </div>
                        );
                    })()}

                    {/* Hai thẻ SA và GA cạnh nhau hoặc từng thẻ */}
                    <Flex gap={16} wrap="wrap" style={{ marginBottom: 16 }}>
                        {saEvaluation ? (
                            <div style={{
                                flex: '1 1 280px',
                                minWidth: 260,
                                padding: 16,
                                borderRadius: 12,
                                border: '1px solid #e8e8e8',
                                borderLeft: '4px solid #1677ff',
                                background: '#fff',
                                boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
                            }}>
                                <div style={{ fontSize: 11, color: '#1677ff', fontWeight: 600, marginBottom: 6, letterSpacing: '0.3px' }}>SIMULATED ANNEALING</div>
                                <div style={{ fontWeight: 600, fontSize: 13, color: '#262626', marginBottom: 10 }}>{saEvaluation.semesterName}</div>
                                <div style={{ display: 'grid', gap: '6px 16px', gridTemplateColumns: 'repeat(2, 1fr)', fontSize: 12, color: '#595959' }}>
                                    <span>Buổi đã xếp</span><strong style={{ color: '#262626' }}>{saEvaluation.assignedCount}</strong>
                                    <span>Xung đột lưu</span><strong style={{ color: '#262626' }}>{saEvaluation.conflictCount}</strong>
                                    <span>Lớp ảnh hưởng</span><strong style={{ color: '#262626' }}>{saEvaluation.unscheduledSections}</strong>
                                    {typeof saEvaluation.fitness === 'number' && <><span>Fitness</span><strong style={{ color: '#262626' }}>{Math.round(saEvaluation.fitness)}</strong></>}
                                    <span>Thời gian</span><strong style={{ color: '#262626' }}>{(saEvaluation.runtimeMs / 1000).toFixed(1)}s</strong>
                                </div>
                                {saEvaluation.overloadWarnings?.length > 0 && (
                                    <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid #f0f0f0', fontSize: 11, color: '#8c8c8c' }}>
                                        {saEvaluation.overloadWarnings.slice(0, 2).map((w, i) => <div key={i}>{w}</div>)}
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div style={{ flex: '1 1 280px', minWidth: 260, padding: 24, borderRadius: 12, background: '#fafafa', border: '1px dashed #d9d9d9', textAlign: 'center', color: '#8c8c8c', fontSize: 13 }}>
                                Chưa chạy SA.<br />Chọn SA trong &quot;Xếp TKB tự động&quot; để có số liệu.
                            </div>
                        )}
                        {gaEvaluation ? (
                            <div style={{
                                flex: '1 1 280px',
                                minWidth: 260,
                                padding: 16,
                                borderRadius: 12,
                                border: '1px solid #e8e8e8',
                                borderLeft: '4px solid #00b9a4',
                                background: '#fff',
                                boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
                            }}>
                                <div style={{ fontSize: 11, color: '#00b9a4', fontWeight: 600, marginBottom: 6, letterSpacing: '0.3px' }}>GENETIC ALGORITHM</div>
                                <div style={{ fontWeight: 600, fontSize: 13, color: '#262626', marginBottom: 10 }}>{gaEvaluation.semesterName}</div>
                                <div style={{ display: 'grid', gap: '6px 16px', gridTemplateColumns: 'repeat(2, 1fr)', fontSize: 12, color: '#595959' }}>
                                    <span>Buổi đã xếp</span><strong style={{ color: '#262626' }}>{gaEvaluation.assignedCount}</strong>
                                    <span>Xung đột lưu</span><strong style={{ color: '#262626' }}>{gaEvaluation.conflictCount}</strong>
                                    <span>Lớp ảnh hưởng</span><strong style={{ color: '#262626' }}>{gaEvaluation.unscheduledSections}</strong>
                                    {typeof gaEvaluation.fitness === 'number' && <><span>Fitness</span><strong style={{ color: '#262626' }}>{Math.round(gaEvaluation.fitness)}</strong></>}
                                    <span>Thời gian</span><strong style={{ color: '#262626' }}>{(gaEvaluation.runtimeMs / 1000).toFixed(1)}s</strong>
                                </div>
                                {gaEvaluation.overloadWarnings?.length > 0 && (
                                    <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid #f0f0f0', fontSize: 11, color: '#8c8c8c' }}>
                                        {gaEvaluation.overloadWarnings.slice(0, 2).map((w, i) => <div key={i}>{w}</div>)}
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div style={{ flex: '1 1 280px', minWidth: 260, padding: 24, borderRadius: 12, background: '#fafafa', border: '1px dashed #d9d9d9', textAlign: 'center', color: '#8c8c8c', fontSize: 13 }}>
                                Chưa chạy GA.<br />Chọn GA trong &quot;Xếp TKB tự động&quot; để có số liệu.
                            </div>
                        )}
                    </Flex>

                    <Collapse
                        ghost
                        size="small"
                        items={[{
                            key: '1',
                            label: <span style={{ fontSize: 12, color: '#8c8c8c' }}>Giải thích chỉ số</span>,
                            children: (
                                <div style={{ fontSize: 12, color: '#595959', lineHeight: 1.7 }}>
                                    <strong>Fitness</strong>: hàm mục tiêu (cao hơn tốt). &nbsp;
                                    <strong>Xung đột</strong>: trùng phòng/GV (ít hơn tốt). &nbsp;
                                    <strong>Buổi đã xếp</strong>: số buổi hợp lệ. &nbsp;
                                    <strong>Lớp ảnh hưởng</strong>: lớp có buổi không xếp được.
                                </div>
                            ),
                        }]}
                    />
                </div>
            </Modal>

            {/* Modal Xếp TKB tự động */}
            <Modal
                title={
                    <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <ThunderboltOutlined style={{ color: '#1890ff', fontSize: 22 }} />
                        <span>Xếp Thời khóa biểu tự động</span>
                    </span>
                }
                open={generateModalOpen}
                onCancel={handleCloseGenerateModal}
                footer={generating ? null : (
                    <Space>
                        <Button onClick={handleCloseGenerateModal}>Hủy</Button>
                        <Button type="primary" icon={<ThunderboltOutlined />} onClick={handleStartGenerate}>
                            Bắt đầu xếp TKB
                        </Button>
                    </Space>
                )}
                closable={!generating}
                mask={{ closable: false }}
                centered
                width={520}
                styles={{ body: { paddingTop: 8, overflow: 'hidden' } }}
            >
                {!generating ? (
                    <div style={{ padding: '12px 0' }}>
                        <div style={{ marginBottom: 16 }}>
                            <div style={STYLES.fieldLabel}>Chọn thuật toán</div>
                            <Select
                                value={selectedAlgorithm}
                                onChange={setSelectedAlgorithm}
                                style={{ width: '100%' }}
                                options={[
                                    { value: 'SA', label: 'Simulated Annealing (SA)' },
                                    { value: 'GA', label: 'Genetic Algorithm (GA)' },
                                ]}
                            />
                        </div>
                        <div style={{
                            padding: 16,
                            background: 'linear-gradient(135deg, #e6f7ff 0%, #f0f5ff 100%)',
                            borderRadius: 12,
                            border: '1px solid #91caff',
                            marginBottom: 16,
                        }}>
                            <p style={{ margin: 0, fontSize: 14, lineHeight: 1.7, color: '#0050b3' }}>
                                Hệ thống sẽ sử dụng <strong>{selectedAlgorithm === 'GA' ? 'Genetic Algorithm (GA)' : 'Simulated Annealing (SA)'}</strong> để xếp TKB tối ưu cho tất cả các lớp đã phân công giảng viên.
                            </p>
                        </div>
                        <ul style={{ margin: 0, paddingLeft: 20, color: '#595959', fontSize: 14, lineHeight: 2 }}>
                            <li><strong>Toàn bộ TKB</strong> (dự kiến + đã xác nhận) sẽ bị xóa để xếp lại từ đầu</li>
                            <li>Chỉ xếp các lớp đã có giảng viên</li>
                            <li>Quá trình có thể mất vài giây đến vài phút</li>
                        </ul>
                    </div>
                ) : (
                    <div style={{ padding: '16px 0', overflow: 'hidden' }}>
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 12,
                            marginBottom: 20,
                            padding: 12,
                            background: 'linear-gradient(90deg, #f6ffed 0%, #e6fffb 100%)',
                            borderRadius: 10,
                            border: '1px solid #b7eb8f',
                        }}>
                            <LoadingOutlined style={{ fontSize: 24, color: '#52c41a' }} spin />
                            <div style={{ flex: 1 }}>
                                <div style={{ fontWeight: 600, fontSize: 15, color: '#389e0d', marginBottom: 4 }}>
                                    {generationStatus || 'Đang xử lý...'}
                                </div>
                                <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                                    {selectedAlgorithm === 'GA' ? 'Genetic Algorithm' : 'Simulated Annealing'} đang tối ưu hóa thời khóa biểu
                                </div>
                            </div>
                        </div>
                        <div style={{ overflow: 'hidden', borderRadius: 4 }}>
                            <Progress
                                percent={generationProgress}
                                status={generationProgress >= 100 ? 'success' : 'active'}
                                strokeColor={{
                                    '0%': '#1890ff',
                                    '100%': '#52c41a',
                                }}
                                size={{ strokeWidth: 12 }}
                                showInfo
                                format={(percent) => (
                                    <span style={{ fontWeight: 600, fontSize: 14 }}>{percent}%</span>
                                )}
                            />
                        </div>
                    </div>
                )}
            </Modal>

            {/* Modal Chỉnh sửa buổi học — 2-column layout */}
            <Modal
                title={
                    <Space size={8}>
                        <EditOutlined style={{ color: '#1890ff' }} />
                        <span>Chỉnh sửa buổi học</span>
                    </Space>
                }
                open={editModalOpen}
                onOk={handleSaveEdit}
                onCancel={() => {
                    setEditModalOpen(false);
                    setSelectedEntry(null);
                    setEditAssignTargetKeys([]);
                    setSuggestions([]);
                }}
                okText="Lưu thay đổi"
                cancelText="Hủy"
                width={1100}
                centered
                styles={{ body: { padding: 0 } }}
            >
                {selectedEntry && (
                    <div style={{ display: 'flex', minHeight: 520 }}>

                            <div style={STYLES.leftPanel}>
                                <div style={STYLES.infoBox}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                                        <Tag
                                            color={selectedEntry.classSection?.sectionType === 'LT' ? 'geekblue' : 'purple'}
                                            style={{ border: 'none', fontWeight: 600 }}
                                        >
                                            {selectedEntry.classSection?.sectionType === 'LT' ? 'Lý thuyết' : 'Thực hành'}
                                        </Tag>
                                        <Text style={{ fontWeight: 700, fontSize: 14, color: '#003a8c' }}>
                                            {selectedEntry.classSection?.code}
                                        </Text>
                                    </div>
                                    <Text type="secondary" style={{ fontSize: 12 }}>
                                        {selectedEntry.classSection?.courseOffering?.course?.name}
                                    </Text>
                                </div>

                                {suggestions.length > 0 && (
                                    <div style={STYLES.suggestionsBox}>
                                        <div style={{ marginBottom: 6, fontWeight: 600, color: '#874d00', fontSize: 12, display: 'flex', alignItems: 'center', gap: 5 }}>
                                            <BulbOutlined /> Gợi ý slot trống
                                        </div>
                                        <Space wrap size={6}>
                                            {suggestions.map((s, i) => (
                                                <Button
                                                    key={i}
                                                    size="small"
                                                    style={{ fontSize: 11 }}
                                                    onClick={() => setEditForm({
                                                        roomId: s.roomId,
                                                        shiftId: s.shiftId,
                                                        dayOfWeek: s.dayOfWeek,
                                                    })}
                                                >
                                                    {s.dayName} · {s.shiftName} · {s.roomName}
                                                </Button>
                                            ))}
                                        </Space>
                                    </div>
                                )}

                                <div>
                                    <div style={STYLES.sectionLabel}>Thông tin lịch học</div>
                                    <Space direction="vertical" size={10} style={{ width: '100%' }}>
                                        <div>
                                            <div style={STYLES.fieldLabel}>Thứ trong tuần</div>
                                            <Select
                                                style={{ width: '100%' }}
                                                value={editForm.dayOfWeek}
                                                onChange={val => setEditForm({ ...editForm, dayOfWeek: val })}
                                                size="middle"
                                            >
                                                {[2, 3, 4, 5, 6, 7].map(d => (
                                                    <Option key={d} value={d}>Thứ {d}</Option>
                                                ))}
                                            </Select>
                                        </div>
                                        <div>
                                            <div style={STYLES.fieldLabel}>Ca học</div>
                                            <Select
                                                style={{ width: '100%' }}
                                                value={editForm.shiftId}
                                                onChange={val => setEditForm({ ...editForm, shiftId: val })}
                                                placeholder="Chọn ca học"
                                                size="middle"
                                            >
                                                {shifts.map(sh => (
                                                    <Option key={sh.id} value={sh.id}>
                                                        {sh.name} — Tiết {sh.startPeriod}–{sh.endPeriod}
                                                    </Option>
                                                ))}
                                            </Select>
                                        </div>
                                        <div>
                                            <div style={STYLES.fieldLabel}>Phòng học</div>
                                            <Select
                                                style={{ width: '100%' }}
                                                value={editForm.roomId}
                                                onChange={val => setEditForm({ ...editForm, roomId: val })}
                                                showSearch
                                                placeholder="Tìm phòng..."
                                                optionFilterProp="label"
                                                filterOption={(input, opt) =>
                                                    String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())
                                                }
                                                options={rooms.map(r => ({
                                                    value: r.id,
                                                    label: `${r.name} (${r.type}, ${r.capacity} chỗ)`,
                                                }))}
                                                size="middle"
                                            />
                                        </div>
                                    </Space>
                                </div>
                            </div>

                            <div style={STYLES.rightPanel}>
                                <div style={{ ...STYLES.sectionLabel, marginBottom: 0 }}>Lớp biên chế</div>
                                <div style={STYLES.adminClassCard}>
                                    {(() => {
                                        const acs = selectedEntry.classSection?.administrativeClasses || [];
                                        return acs.length === 0 ? (
                                            <div style={{ textAlign: 'center', padding: '40px 0', color: '#bbb' }}>
                                                <TeamOutlined style={{ fontSize: 28, marginBottom: 8, display: 'block' }} />
                                                <div style={{ fontSize: 12 }}>Chưa gán lớp biên chế</div>
                                                <div style={{ fontSize: 11, marginTop: 4, color: '#ccc' }}>
                                                    Thực hiện tại Quản lý lớp học phần
                                                </div>
                                            </div>
                                        ) : (
                                            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                                                {acs.map(ac => (
                                                    <div key={ac.id} style={STYLES.acItem}>
                                                        <Tag color="cyan" style={{ border: 'none', fontSize: 11, padding: '0 6px', margin: 0, fontWeight: 600 }}>
                                                            {ac.code}
                                                        </Tag>
                                                        <span style={{ fontSize: 12, color: '#444', flex: 1 }}>{ac.name}</span>
                                                        {ac.studentCount != null && (
                                                            <span style={{ fontSize: 11, color: '#888', fontWeight: 600 }}>{ac.studentCount} SV</span>
                                                        )}
                                                    </div>
                                                ))}
                                                <div style={{
                                                    marginTop: 8, paddingTop: 8,
                                                    borderTop: '1px dashed #eef0f3',
                                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                                }}>
                                                    <span style={{ fontSize: 11, color: '#888' }}>Tổng lớp biên chế</span>
                                                    <span style={{ fontWeight: 700, color: '#005a8d' }}>{acs.length} lớp</span>
                                                </div>
                                            </div>
                                        );
                                    })()}
                                </div>
                                <div style={{ fontSize: 11, color: '#aaa', textAlign: 'center', padding: '4px 0' }}>
                                    Chỉnh sửa phân công tại "Quản lý lớp học phần"
                                </div>
                            </div>

                        </div>
                )}
            </Modal>
        </div>
    );
};

export default TimetableManagement;
