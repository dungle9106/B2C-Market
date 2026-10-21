import { exportPlatformReport } from '../../../api/admin'

// the three platform report pages share the period bar (AnalyticsToolbar) and one Excel export
export const REPORT_TABS = [
  { name: 'admin-report-overview', label: 'Tổng quan kinh doanh' },
  { name: 'admin-report-top', label: 'Top shop & sản phẩm' },
  { name: 'admin-report-growth', label: 'Tăng trưởng' }
]
export const exportReport = exportPlatformReport
