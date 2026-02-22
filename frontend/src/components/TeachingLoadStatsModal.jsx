import React from 'react';
import { Modal, Table, Tag, Alert, Divider, Typography } from 'antd';
import {
    BarChartOutlined,
    TeamOutlined,
    AppstoreOutlined,
    RiseOutlined,
    SwapOutlined,
} from '@ant-design/icons';
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    ReferenceLine,
    Cell,
} from 'recharts';

const { Text } = Typography;

/* ── Palette ────────────────────────────────────────────── */
const PRIMARY = '#005a8d';
const ACCENT = '#1890ff';
const SUCCESS = '#52c41a';
const WARNING = '#fa8c16';
const PURPLE = '#722ed1';
const DANGER = '#f5222d';
const GREY_BG = '#f8f9fb';
const BORDER = '#eef0f3';

/* ── Metric chip ────────────────────────────────────────── */
const MetricChip = ({ icon, label, value, color }) => (
    <div style={{
        flex: '1 1 0',
        minWidth: 0,
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 14px',
        borderRadius: 10,
        background: '#fff',
        border: `1px solid ${BORDER}`,
    }}>
        <div style={{
            width: 34,
            height: 34,
            borderRadius: 8,
            background: `${color}15`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color,
            fontSize: 16,
            flexShrink: 0,
        }}>
            {icon}
        </div>
        <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 11, color: '#8c8c8c', whiteSpace: 'nowrap', lineHeight: 1.2 }}>
                {label}
            </div>
            <div style={{ fontSize: 20, fontWeight: 700, color: '#1a1a1a', lineHeight: 1.3 }}>
                {value}
            </div>
        </div>
    </div>
);

/* ── Custom bar tooltip ─────────────────────────────────── */
const BarTooltip = ({ active, payload }) => {
    if (!active || !payload?.length) return null;
    const d = payload[0];
    return (
        <div style={{
            background: '#fff',
            border: `1px solid ${BORDER}`,
            borderRadius: 8,
            padding: '8px 12px',
            fontSize: 13,
            boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
        }}>
            <div style={{ fontWeight: 600, color: '#1a1a1a', marginBottom: 2 }}>
                {d.payload?.name}
            </div>
            <div style={{ color: d.fill }}>
                <span style={{ color: '#8c8c8c', marginRight: 4 }}>Số lớp:</span>
                <strong>{d.value}</strong>
            </div>
        </div>
    );
};

/* ── Main component ─────────────────────────────────────── */
const TeachingLoadStatsModal = ({ open, onClose, teachingLoad, semesterName, facultyName }) => {
    const totalSections = teachingLoad.reduce((s, d) => s + (d.sectionCount || 0), 0);
    const totalLecturers = teachingLoad.length;
    const avgLoad = totalLecturers > 0 ? (totalSections / totalLecturers).toFixed(1) : 0;
    const maxLoad = teachingLoad.length > 0 ? Math.max(...teachingLoad.map(d => d.sectionCount || 0)) : 0;
    const minLoad = teachingLoad.length > 0 ? Math.min(...teachingLoad.map(d => d.sectionCount || 0)) : 0;

    // Bar: đánh số 1,2,3... để tránh chồng chéo tên; dóng sang bảng
    const chartData = teachingLoad.map((d, i) => ({
        num: i + 1,
        name: d.lecturerName,
        soLop: d.sectionCount || 0,
    }));

    return (
        <Modal
            title={
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, paddingBottom: 2 }}>
                    <div style={{
                        width: 32, height: 32, borderRadius: 8,
                        background: `${PRIMARY}15`,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        color: PRIMARY, fontSize: 16,
                    }}>
                        <BarChartOutlined />
                    </div>
                    <div>
                        <div style={{ fontSize: 15, fontWeight: 700, color: '#1a1a1a', lineHeight: 1.3 }}>
                            Thống kê tải giảng
                        </div>
                        {(semesterName || facultyName) && (
                            <div style={{ fontSize: 12, color: '#8c8c8c', fontWeight: 400 }}>
                                {[facultyName, semesterName].filter(Boolean).join(' · ')}
                            </div>
                        )}
                    </div>
                </div>
            }
            open={open}
            onCancel={onClose}
            footer={null}
            width={820}
            centered
            destroyOnClose
            styles={{
                header: { paddingBottom: 12, borderBottom: `1px solid ${BORDER}` },
                body: { padding: '20px 24px', background: GREY_BG },
                mask: { backdropFilter: 'blur(4px)', background: 'rgba(0,0,0,0.25)' },
            }}
        >
            {teachingLoad.length === 0 ? (
                <div style={{ padding: '60px 0' }}>
                    <Alert
                        message="Chưa có dữ liệu"
                        description="Vui lòng thực hiện phân công giảng viên trước khi xem thống kê."
                        type="info"
                        showIcon
                    />
                </div>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

                    {/* ── Metric chips ── */}
                    <div style={{ display: 'flex', gap: 10 }}>
                        <MetricChip icon={<AppstoreOutlined />} label="Tổng lớp" value={totalSections} color={PRIMARY} />
                        <MetricChip icon={<TeamOutlined />} label="Giảng viên" value={totalLecturers} color={SUCCESS} />
                        <MetricChip icon={<RiseOutlined />} label="Tải TB / GV" value={avgLoad} color={WARNING} />
                        <MetricChip icon={<SwapOutlined />} label="Độ lệch tải" value={maxLoad - minLoad} color={PURPLE} />
                    </div>

                    {/* ── Chart + Table (cùng chiều cao) ── */}
                    <div style={{ display: 'flex', gap: 14, alignItems: 'stretch', minHeight: 360 }}>

                        {/* Bar chart */}
                        <div style={{
                            flex: '1 1 0',
                            minWidth: 0,
                            background: '#fff',
                            borderRadius: 12,
                            border: `1px solid ${BORDER}`,
                            padding: '16px 16px 8px',
                            display: 'flex',
                            flexDirection: 'column',
                        }}>
                            <div style={{ marginBottom: 12, flexShrink: 0 }}>
                                <Text strong style={{ fontSize: 13, display: 'block' }}>
                                    Phân bố số lớp theo giảng viên
                                </Text>
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                    Màu đỏ = vượt tải trung bình
                                </Text>
                            </div>
                            <div style={{ flex: 1, minHeight: 0 }}>
                                <ResponsiveContainer width="100%" height="100%">
                                <BarChart
                                    data={chartData}
                                    margin={{ top: 4, right: 14, left: -14, bottom: 32 }}
                                    barCategoryGap="28%"
                                >
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                                    <XAxis
                                        dataKey="num"
                                        interval={0}
                                        tick={{ fontSize: 11, fill: '#8c8c8c', fontWeight: 600 }}
                                        axisLine={false}
                                        tickLine={false}
                                    />
                                    <YAxis
                                        allowDecimals={false}
                                        tick={{ fontSize: 10, fill: '#8c8c8c' }}
                                        axisLine={false}
                                        tickLine={false}
                                    />
                                    <Tooltip content={<BarTooltip />} />
                                    <ReferenceLine
                                        y={parseFloat(avgLoad)}
                                        stroke={WARNING}
                                        strokeDasharray="4 3"
                                        label={{
                                            value: `TB ${avgLoad}`,
                                            position: 'insideTopRight',
                                            fill: WARNING,
                                            fontSize: 10,
                                            fontWeight: 600,
                                        }}
                                    />
                                    <Bar dataKey="soLop" name="Số lớp" radius={[4, 4, 0, 0]}>
                                        {chartData.map((entry, index) => (
                                            <Cell
                                                key={`cell-${index}`}
                                                fill={entry.soLop > parseFloat(avgLoad) ? DANGER : ACCENT}
                                                fillOpacity={0.85}
                                            />
                                        ))}
                                    </Bar>
                                </BarChart>
                                </ResponsiveContainer>
                            </div>
                        </div>

                        {/* Bảng Ant Design */}
                        <div style={{
                            width: 280,
                            flexShrink: 0,
                            background: '#fff',
                            borderRadius: 12,
                            border: `1px solid ${BORDER}`,
                            padding: '16px 14px 8px',
                            display: 'flex',
                            flexDirection: 'column',
                            minHeight: 0,
                        }}>
                            <div style={{ marginBottom: 12, flexShrink: 0 }}>
                                <Text strong style={{ fontSize: 13, display: 'block' }}>
                                    Chi tiết
                                </Text>
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                    Số trong biểu đồ → dóng sang bảng
                                </Text>
                            </div>
                            <Table
                                size="small"
                                dataSource={teachingLoad}
                                rowKey="lecturerId"
                                pagination={{
                                    pageSize: 8,
                                    size: 'small',
                                    showSizeChanger: false,
                                    showTotal: (total) => `${total} GV`,
                                }}
                                showHeader={true}
                                className="load-table"
                                columns={[
                                    {
                                        title: '#',
                                        key: 'num',
                                        align: 'center',
                                        width: 36,
                                        render: (_, __, idx) => (
                                            <Text strong style={{ fontSize: 12, color: '#005a8d' }}>{idx + 1}</Text>
                                        ),
                                    },
                                    {
                                        title: 'Họ và tên',
                                        dataIndex: 'lecturerName',
                                        ellipsis: true,
                                        render: (t) => (
                                            <Text style={{ fontSize: 12, color: '#1a1a1a' }}>{t}</Text>
                                        ),
                                    },
                                    {
                                        title: 'Lớp',
                                        dataIndex: 'sectionCount',
                                        align: 'center',
                                        width: 44,
                                        render: (n) => (
                                            <Tag
                                                style={{
                                                    border: 'none',
                                                    borderRadius: 6,
                                                    fontSize: 12,
                                                    fontWeight: 700,
                                                    padding: '0 6px',
                                                    background: n > parseFloat(avgLoad) ? '#fff1f0' : '#f0f9eb',
                                                    color: n > parseFloat(avgLoad) ? DANGER : SUCCESS,
                                                }}
                                            >
                                                {n}
                                            </Tag>
                                        ),
                                    },
                                    {
                                        title: '%',
                                        key: 'pct',
                                        align: 'right',
                                        width: 38,
                                        render: (_, r) => (
                                            <Text type="secondary" style={{ fontSize: 11 }}>
                                                {totalSections > 0
                                                    ? ((r.sectionCount / totalSections) * 100).toFixed(0)
                                                    : 0}%
                                            </Text>
                                        ),
                                    },
                                ]}
                            />
                        </div>
                    </div>
                </div>
            )}

            <style>{`
                .load-table .ant-table { background: transparent; font-size: 12px; }
                .load-table .ant-table-thead > tr > th {
                    background: transparent;
                    border-bottom: 1.5px solid ${BORDER};
                    font-size: 11px;
                    text-transform: uppercase;
                    color: #aaa;
                    letter-spacing: 0.4px;
                    padding: 6px 8px !important;
                }
                .load-table .ant-table-tbody > tr > td {
                    border-bottom: 1px solid ${BORDER};
                    padding: 7px 8px !important;
                }
                .load-table .ant-table-tbody > tr:hover > td { background: ${GREY_BG} !important; }
                .load-table .ant-pagination {
                    margin: 10px 0 0 !important;
                    display: flex;
                    justify-content: center;
                    gap: 4px;
                }
                .load-table .ant-pagination .ant-pagination-prev,
                .load-table .ant-pagination .ant-pagination-next,
                .load-table .ant-pagination .ant-pagination-item {
                    min-width: 28px;
                    height: 28px;
                    line-height: 26px;
                    border-radius: 8px;
                    border: 1px solid ${BORDER};
                    background: #fff;
                }
                .load-table .ant-pagination .ant-pagination-item a { color: #666; }
                .load-table .ant-pagination .ant-pagination-item-active {
                    background: ${PRIMARY}15;
                    border-color: ${PRIMARY};
                }
                .load-table .ant-pagination .ant-pagination-item-active a { color: ${PRIMARY}; font-weight: 600; }
                .load-table .ant-pagination .ant-pagination-prev .ant-pagination-item-link,
                .load-table .ant-pagination .ant-pagination-next .ant-pagination-item-link {
                    border-radius: 8px;
                    border: 1px solid ${BORDER};
                }
                .load-table .ant-pagination .ant-pagination-disabled .ant-pagination-item-link { opacity: 0.5; }
                .load-table .ant-pagination .ant-pagination-total-text {
                    font-size: 12px;
                    color: #8c8c8c;
                    margin-right: 12px;
                }
            `}</style>
        </Modal>
    );
};

export default TeachingLoadStatsModal;
