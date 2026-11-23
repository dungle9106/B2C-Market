// Building blocks shared by the admin app screens: labels (same as frontend/src/utils/adminLabels.js and
// returns.js), a paged list loader, and a dialog that asks for a reason (Alert.prompt only exists on iOS).
import React, { createContext, useCallback, useContext, useRef, useState } from 'react'
import { FlatList, Modal, Pressable, RefreshControl, Text, View } from 'react-native'
import { useFocusEffect } from '@react-navigation/native'
import { get, errorText } from '../api'
import { formatDateTime } from '../format'
import { Button, C, Empty, Field, Loading, styles } from '../ui'

export const SHOP_STATUS = { PENDING: 'Chờ duyệt', APPROVED: 'Đang hoạt động', REJECTED: 'Bị từ chối', SUSPENDED: 'Tạm đình chỉ' }
export const PRODUCT_STATUS = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối', REMOVED: 'Đã gỡ' }
export const USER_STATUS = { ACTIVE: 'Đang hoạt động', LOCKED: 'Đang khóa', DELETED: 'Đã xóa' }
export const ROLE = { BUYER: 'Người mua', SELLER: 'Người bán', ADMIN: 'Quản trị' }
export const PAYMENT_METHOD = { COD: 'COD', E_WALLET: 'Ví điện tử', CARD: 'Thẻ', BANK_TRANSFER: 'Chuyển khoản', VNPAY: 'VNPay', SHOPPI_PAY: 'ShoppiPay', MOCK_ONLINE: 'Online' }
export const PAYMENT_STATUS = { PENDING: 'Chưa thanh toán', PAID: 'Đã thanh toán', FAILED: 'Lỗi', CANCELLED: 'Đã hủy', REFUNDED: 'Đã hoàn tiền' }
export const RETURN_REASONS = { DAMAGED: 'Hàng bị hư hỏng, lỗi', WRONG_ITEM: 'Giao sai hàng / sai phân loại', NOT_AS_DESCRIBED: 'Hàng không đúng mô tả',
  MISSING_ITEM: 'Thiếu hàng', FAKE: 'Hàng giả, hàng nhái', OTHER: 'Lý do khác' }
export const RETURN_STATUS = { REQUESTED: 'Chờ người bán phản hồi', REJECTED: 'Người bán từ chối', ESCALATED: 'Đang khiếu nại', REFUNDED: 'Đã hoàn tiền', CLOSED: 'Đã đóng' }
export const DISPUTE_DECISION = { REFUND_FULL: 'Hoàn toàn bộ', REFUND_PARTIAL: 'Hoàn một phần', REJECT: 'Không hoàn tiền' }
export const REFUND_STATUS = { PENDING: 'Chờ chuyển khoản', COMPLETED: 'Đã hoàn tiền', FAILED: 'Thất bại' }
export const REFUND_SOURCE = { ORDER_CANCELLED: 'Hủy đơn', RETURN: 'Trả hàng/Hoàn tiền', DISPUTE: 'Giải quyết khiếu nại', ADMIN: 'Shoppi hoàn thủ công' }
// what an admin did (audit log), same as the web
export const AUDIT_ACTION = {
  LOCK_USER: 'Khóa tài khoản',
  UNLOCK_USER: 'Mở khóa tài khoản',
  DELETE_USER: 'Xóa tài khoản',
  APPROVE_SHOP: 'Duyệt shop',
  REJECT_SHOP: 'Từ chối shop',
  SUSPEND_SHOP: 'Đình chỉ shop',
  REINSTATE_SHOP: 'Mở lại shop',
  CREATE_STAFF: 'Thêm nhân viên',
  UPDATE_STAFF: 'Đổi quyền nhân viên',
  REVOKE_STAFF: 'Thu hồi quyền quản trị',
  UPDATE_SETTING: 'Đổi cài đặt sàn',
  APPROVE_PRODUCT: 'Duyệt sản phẩm',
  REJECT_PRODUCT: 'Từ chối sản phẩm',
  REMOVE_PRODUCT: 'Gỡ sản phẩm vi phạm',
  RESTORE_PRODUCT: 'Khôi phục sản phẩm',
  DISMISS_REPORTS: 'Bỏ qua báo cáo',
  CREATE_CATEGORY: 'Thêm danh mục',
  UPDATE_CATEGORY: 'Sửa danh mục',
  HIDE_CATEGORY: 'Ẩn danh mục',
  SHOW_CATEGORY: 'Hiện danh mục',
  DELETE_CATEGORY: 'Xóa danh mục',
  CANCEL_ORDER: 'Hủy đơn hàng',
  MANUAL_REFUND: 'Hoàn tiền thủ công',
  COMPLETE_REFUND: 'Xác nhận đã hoàn tiền',
  FAIL_REFUND: 'Hoàn tiền thất bại',
  RESOLVE_DISPUTE: 'Giải quyết tranh chấp',
  CREATE_SETTLEMENT: 'Tạo bảng đối soát',
  PAY_SETTLEMENT: 'Thanh toán cho người bán',
  CREATE_VOUCHER: 'Tạo Shoppi Voucher',
  UPDATE_VOUCHER: 'Sửa Shoppi Voucher',
  END_VOUCHER: 'Kết thúc Shoppi Voucher',
  PAUSE_VOUCHER: 'Tạm dừng Shoppi Voucher',
  RESUME_VOUCHER: 'Tiếp tục Shoppi Voucher',
  CREATE_CAMPAIGN: 'Tạo chương trình khuyến mãi',
  UPDATE_CAMPAIGN: 'Sửa chương trình khuyến mãi',
  SHOW_CAMPAIGN: 'Hiện chương trình',
  HIDE_CAMPAIGN: 'Ẩn chương trình',
  DELETE_CAMPAIGN: 'Xóa chương trình',
  CREATE_BANNER: 'Thêm banner',
  UPDATE_BANNER: 'Sửa banner',
  SHOW_BANNER: 'Hiện banner',
  HIDE_BANNER: 'Ẩn banner',
  DELETE_BANNER: 'Xóa banner',
  CREATE_FLASH_EVENT: 'Tạo khung giờ Flash Sale',
  UPDATE_FLASH_EVENT: 'Sửa khung giờ Flash Sale',
  CANCEL_FLASH_EVENT: 'Hủy khung giờ Flash Sale',
  APPROVE_FLASH_ITEM: 'Duyệt sản phẩm Flash Sale',
  REJECT_FLASH_ITEM: 'Từ chối sản phẩm Flash Sale',
  EXPORT_REPORT: 'Xuất báo cáo toàn sàn',
  UPDATE_AI_CONFIG: 'Đổi cấu hình AI',
  PUBLISH_POLICY: 'Xuất bản chính sách',
  RESOLVE_ERROR: 'Đánh dấu lỗi đã xử lý',
  IGNORE_ERROR: 'Bỏ qua lỗi',
  REOPEN_ERROR: 'Mở lại lỗi'
}

export const STATUS_COLOR = { PENDING: '#f69113', APPROVED: C.green, ACTIVE: C.green, REJECTED: C.danger, SUSPENDED: C.danger, REMOVED: C.danger, LOCKED: C.danger,
  DELETED: C.faint, COMPLETED: C.green, FAILED: C.danger, ESCALATED: '#f69113', OPEN: '#f69113', IN_PROGRESS: '#2673dd', RESOLVED: C.green, CLOSED: C.faint }

// what the signed-in admin may do (GET /admin/me): the super admin has every permission
export const AdminContext = createContext({ me: null, can: () => false, reloadMe: () => {} })
export const useAdmin = () => useContext(AdminContext)

/**
 * A paged admin list (PageResponse: { items, page, totalPages, totalItems }), reloaded when the screen is focused
 * or the params change; more() loads the next page.
 */
export function usePaged(path, params, deps = []) {
  const [state, setState] = useState({ items: null, page: 0, totalPages: 0, totalItems: 0, error: null })
  const [refreshing, setRefreshing] = useState(false)
  const loading = useRef(false)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(async (page = 0, pull = false) => {
    if (loading.current) return
    loading.current = true
    if (pull) setRefreshing(true)
    try {
      const r = await get(path, { ...params, page, size: 20 })
      setState(s => ({ items: page === 0 ? r.items : [...(s.items || []), ...r.items], page: r.page, totalPages: r.totalPages, totalItems: r.totalItems, error: null }))
    } catch (e) {
      setState(s => ({ ...s, items: s.items || [], error: errorText(e) }))
    } finally {
      loading.current = false
      setRefreshing(false)
    }
  }, [path, JSON.stringify(params), ...deps])
  useFocusEffect(useCallback(() => { load(0) }, [load]))
  return {
    ...state, refreshing,
    reload: () => load(0),
    refresh: () => load(0, true),
    more: () => { if (state.page + 1 < state.totalPages) load(state.page + 1) }
  }
}

/** FlatList over usePaged(): pull to refresh, load more at the end, empty / error states. */
export function PagedList({ list, renderItem, header, empty = 'Không có dữ liệu', ...props }) {
  if (!list.items) return list.error ? <Empty error={list.error} onRetry={list.reload} /> : <Loading />
  return (
    <FlatList data={list.items} keyExtractor={(x, i) => String(x.id ?? i)} renderItem={renderItem}
              ListHeaderComponent={header} onEndReached={list.more} onEndReachedThreshold={0.4}
              refreshControl={<RefreshControl refreshing={list.refreshing} onRefresh={list.refresh} />}
              ListEmptyComponent={<Empty text={list.error || empty} />}
              ListFooterComponent={list.items.length && list.page + 1 < list.totalPages ? <Text style={[styles.muted, { textAlign: 'center', padding: 12 }]}>Đang tải thêm...</Text> : null}
              {...props} />
  )
}

/**
 * const [dialog, ask] = useReason()  ...  const reason = await ask({ title, placeholder, required, confirm, presets })
 * Resolves to the text (or '' when optional and left empty), or null when cancelled. Render {dialog} once in the screen.
 */
export function useReason() {
  const [opts, setOpts] = useState(null)
  const [text, setText] = useState('')
  const resolver = useRef(null)
  const ask = useCallback((o) => new Promise((resolve) => {
    resolver.current = resolve
    setText(o.initial || '')
    setOpts(o)
  }), [])
  const close = (value) => {
    setOpts(null)
    resolver.current?.(value)
    resolver.current = null
  }
  const dialog = (
    <Modal visible={!!opts} transparent animationType="fade" onRequestClose={() => close(null)}>
      <Pressable style={{ flex: 1, backgroundColor: 'rgba(0,0,0,.45)', justifyContent: 'center', padding: 24 }} onPress={() => close(null)}>
        <Pressable style={{ backgroundColor: 'white', borderRadius: 6, padding: 16 }} onPress={() => {}}>
          <Text style={styles.h2}>{opts?.title}</Text>
          {opts?.message ? <Text style={[styles.muted, { marginBottom: 10 }]}>{opts.message}</Text> : null}
          {opts?.presets?.length ? (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 10 }}>
              {opts.presets.map(p => (
                <Pressable key={p} onPress={() => setText(p)} style={{ borderWidth: 1, borderColor: text === p ? C.primary : C.border, borderRadius: 14, paddingHorizontal: 10, paddingVertical: 4 }}>
                  <Text style={{ fontSize: 12, color: text === p ? C.primary : C.text }}>{p}</Text>
                </Pressable>
              ))}
            </View>
          ) : null}
          {opts?.noInput ? null : (
            <Field value={text} onChangeText={setText} placeholder={opts?.placeholder || 'Lý do'} multiline={!opts?.keyboardType}
                   keyboardType={opts?.keyboardType} autoFocus />
          )}
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Button kind="ghost" title="Hủy" onPress={() => close(null)} style={{ flex: 1 }} />
            <Button kind={opts?.danger ? 'danger' : 'primary'} title={opts?.confirm || 'Xác nhận'} style={{ flex: 1 }}
                    disabled={!opts?.noInput && opts?.required !== false && !text.trim()} onPress={() => close(text.trim())} />
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  )
  return [dialog, ask]
}

/** A small "label: value" line used on detail screens. */
export function Info({ label, children, bold }) {
  if (children === null || children === undefined || children === '') return null
  return (
    <View style={{ flexDirection: 'row', paddingVertical: 3 }}>
      <Text style={[styles.muted, { width: 120 }]}>{label}</Text>
      <Text style={{ flex: 1, color: C.text, fontWeight: bold ? '600' : 'normal' }}>{children}</Text>
    </View>
  )
}

/** The audit trail of a user / product / order ("history" in the admin detail responses). */
export function History({ items }) {
  if (!items?.length) return null
  return (
    <View style={styles.card}>
      <Text style={styles.h2}>Lịch sử xử lý</Text>
      {items.map(h => (
        <View key={h.id} style={{ paddingVertical: 5, borderTopWidth: 1, borderTopColor: C.border }}>
          <Text>{AUDIT_ACTION[h.action] || h.action}{h.detail ? <Text style={styles.muted}> · {h.detail}</Text> : null}</Text>
          <Text style={[styles.muted, { fontSize: 12 }]}>{h.adminName} · {formatDateTime(h.createdAt)}</Text>
        </View>
      ))}
    </View>
  )
}

/** A horizontally scrollable status filter; items: [{ key, label, count }]. */
export function Chips({ items, value, onChange }) {
  return (
    <FlatList horizontal data={items} keyExtractor={x => String(x.key)} showsHorizontalScrollIndicator={false}
              style={{ backgroundColor: 'white', flexGrow: 0, borderBottomWidth: 1, borderBottomColor: C.border }}
              contentContainerStyle={{ padding: 8, gap: 6 }}
              renderItem={({ item }) => (
                <Pressable onPress={() => onChange(item.key)} style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16,
                  backgroundColor: value === item.key ? C.primarySoft : C.bg, borderWidth: 1, borderColor: value === item.key ? C.primary : C.bg }}>
                  <Text style={{ fontSize: 13, color: value === item.key ? C.primary : C.text }}>{item.label}{item.count ? ` (${item.count})` : ''}</Text>
                </Pressable>
              )} />
  )
}
