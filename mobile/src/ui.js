// Shared building blocks (Shopee-like look: orange #ee4d2d, white cards on a light grey page)
import React, { useCallback, useState } from 'react'
import { ActivityIndicator, FlatList, Image, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native'
import { useFocusEffect } from '@react-navigation/native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { fileUrl, errorText } from './api'
import { formatVnd, compact } from './format'

export const C = {
  primary: '#ee4d2d',
  primarySoft: '#fff0eb',
  bg: '#f5f5f5',
  card: '#ffffff',
  text: '#222222',
  muted: '#757575',
  faint: '#bdbdbd',
  border: '#e8e8e8',
  green: '#26aa99',
  danger: '#d0011b'
}

// Loads data whenever the screen comes into focus; reload() for pull-to-refresh and after actions.
export function useLoad(fn, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [refreshing, setRefreshing] = useState(false)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(async (pull = false) => {
    if (pull) setRefreshing(true)
    try {
      setData(await fn())
      setError(null)
    } catch (e) {
      setError(errorText(e))
    } finally {
      setRefreshing(false)
    }
  }, deps)
  useFocusEffect(useCallback(() => { run() }, [run]))
  return { data, setData, error, refreshing, reload: run, refresh: () => run(true) }
}

export function Button({ title, onPress, kind = 'primary', small, disabled, style }) {
  const s = kind === 'primary' ? styles.btnPrimary : kind === 'danger' ? styles.btnDanger : styles.btnGhost
  const t = kind === 'primary' ? styles.btnTextPrimary : kind === 'danger' ? styles.btnTextDanger : styles.btnTextGhost
  return (
    <Pressable onPress={onPress} disabled={disabled}
               style={({ pressed }) => [styles.btn, small && styles.btnSmall, s, (disabled || pressed) && { opacity: disabled ? 0.45 : 0.75 }, style]}>
      <Text style={[styles.btnText, small && { fontSize: 13 }, t]}>{title}</Text>
    </Pressable>
  )
}

export function Field({ label, style, ...props }) {
  return (
    <View style={[{ marginBottom: 12 }, style]}>
      {label ? <Text style={styles.label}>{label}</Text> : null}
      <TextInput placeholderTextColor={C.faint} style={[styles.input, props.multiline && { minHeight: 80, textAlignVertical: 'top' }]} {...props} />
    </View>
  )
}

export const Card = ({ children, style }) => <View style={[styles.card, style]}>{children}</View>

export const Loading = () => <View style={styles.center}><ActivityIndicator color={C.primary} size="large" /></View>

export function Empty({ text, error, onRetry }) {
  return (
    <View style={styles.center}>
      <Text style={{ color: error ? C.danger : C.muted, textAlign: 'center' }}>{error || text}</Text>
      {error && onRetry ? <Button title="Thử lại" kind="ghost" small onPress={onRetry} style={{ marginTop: 12 }} /> : null}
    </View>
  )
}

export function Img({ uri, style }) {
  const url = fileUrl(uri)
  return url ? <Image source={{ uri: url }} style={[{ backgroundColor: '#eee' }, style]} />
    : <View style={[{ backgroundColor: '#eee', alignItems: 'center', justifyContent: 'center' }, style]}><Text style={{ color: C.faint }}>🛍</Text></View>
}

export const Badge = ({ text, color = C.primary, bg }) => (
  <Text style={[styles.badge, { color, backgroundColor: bg || color + '1a' }]}>{text}</Text>
)

export function Tabs({ items, value, onChange }) {
  return (
    <View style={styles.tabs}>
      {items.map(t => (
        <Pressable key={t.key} onPress={() => onChange(t.key)} style={[styles.tab, value === t.key && styles.tabActive]}>
          <Text numberOfLines={1} style={[styles.tabText, value === t.key && { color: C.primary }]}>
            {t.label}{t.count ? ` (${t.count})` : ''}
          </Text>
        </Pressable>
      ))}
    </View>
  )
}

export function ProductCard({ p, onPress }) {
  return (
    <Pressable onPress={onPress} style={styles.product}>
      <Img uri={p.thumbnailUrl} style={{ width: '100%', aspectRatio: 1 }} />
      {p.discountPercent ? <Text style={styles.discount}>-{p.discountPercent}%</Text> : null}
      <View style={{ padding: 8, flex: 1 }}>
        <Text numberOfLines={2} style={{ fontSize: 13, color: C.text, minHeight: 34 }}>
          {p.shopMall ? <Text style={styles.mall}> Mall </Text> : p.shopFavorite ? <Text style={styles.fav}> Yêu thích </Text> : null}{p.shopMall || p.shopFavorite ? ' ' : ''}{p.name}
        </Text>
        <View style={{ flexDirection: 'row', gap: 4, marginTop: 4, minHeight: 16 }}>
          {p.freeShipping ? <Badge text="Freeship" color={C.green} /> : null}
          {p.flashSale ? <Badge text="⚡ Flash Sale" /> : null}
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', marginTop: 6 }}>
          <Text style={{ color: C.primary, fontSize: 15, fontWeight: '600' }}>{formatVnd(p.price)}</Text>
          <Text style={{ color: C.muted, fontSize: 11 }}>Đã bán {compact(p.soldCount)}</Text>
        </View>
      </View>
    </Pressable>
  )
}

// bar pinned to the bottom of a screen, kept above Android's navigation bar (edge-to-edge)
export function BottomBar({ children, tight }) {
  const { bottom } = useSafeAreaInsets()
  return <View style={[styles.bottomBar, tight && { padding: 0, gap: 0 }, { paddingBottom: (tight ? 0 : 10) + bottom }]}>{children}</View>
}

// a tappable field that opens a list of options
export function Select({ label, value, options, onChange, placeholder = 'Chọn' }) {
  const [open, setOpen] = useState(false)
  const items = options.map(o => (typeof o === 'object' ? o : { value: o, label: o }))
  const current = items.find(o => o.value === value)
  return (
    <View style={{ marginBottom: 12 }}>
      {label ? <Text style={styles.label}>{label}</Text> : null}
      <Pressable onPress={() => setOpen(true)} style={[styles.input, { flexDirection: 'row', justifyContent: 'space-between' }]}>
        <Text style={{ color: current ? C.text : C.faint, fontSize: 15, flex: 1 }} numberOfLines={1}>{current ? current.label : placeholder}</Text>
        <Text style={{ color: C.faint }}>▾</Text>
      </Pressable>
      <Modal visible={open} transparent animationType="slide" onRequestClose={() => setOpen(false)}>
        <Pressable style={{ flex: 1, backgroundColor: 'rgba(0,0,0,.4)' }} onPress={() => setOpen(false)} />
        <View style={{ backgroundColor: 'white', maxHeight: '60%' }}>
          <Text style={[styles.h2, { padding: 14, marginBottom: 0, borderBottomWidth: 1, borderBottomColor: C.border }]}>{label || placeholder}</Text>
          <FlatList data={items} keyExtractor={o => String(o.value)}
                    renderItem={({ item }) => (
                      <Pressable onPress={() => { onChange(item.value); setOpen(false) }} style={{ padding: 14, borderBottomWidth: 1, borderBottomColor: C.border }}>
                        <Text style={{ color: item.value === value ? C.primary : C.text }}>{item.label}</Text>
                        {item.hint ? <Text style={styles.muted}>{item.hint}</Text> : null}
                      </Pressable>
                    )} />
        </View>
      </Modal>
    </View>
  )
}

export const Row = ({ children, style }) => <View style={[{ flexDirection: 'row', alignItems: 'center' }, style]}>{children}</View>

export function Stars({ value, size = 12 }) {
  const full = Math.round(value || 0)
  return <Text style={{ color: '#ffce3d', fontSize: size }}>{'★'.repeat(full)}<Text style={{ color: C.faint }}>{'★'.repeat(5 - full)}</Text></Text>
}

export const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: C.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, minHeight: 200 },
  card: { backgroundColor: C.card, padding: 14, marginBottom: 8 },
  h2: { fontSize: 16, fontWeight: '600', color: C.text, marginBottom: 8 },
  muted: { color: C.muted, fontSize: 13 },
  label: { color: C.muted, fontSize: 13, marginBottom: 4 },
  input: { borderWidth: 1, borderColor: C.border, borderRadius: 4, paddingHorizontal: 12, paddingVertical: 9, fontSize: 15, color: C.text, backgroundColor: 'white' },
  btn: { paddingVertical: 11, paddingHorizontal: 16, borderRadius: 4, alignItems: 'center', justifyContent: 'center', borderWidth: 1 },
  btnSmall: { paddingVertical: 6, paddingHorizontal: 12 },
  btnPrimary: { backgroundColor: C.primary, borderColor: C.primary },
  btnGhost: { backgroundColor: 'white', borderColor: C.border },
  btnDanger: { backgroundColor: 'white', borderColor: C.danger },
  btnText: { fontSize: 15, fontWeight: '500' },
  btnTextPrimary: { color: 'white' },
  btnTextGhost: { color: C.text },
  btnTextDanger: { color: C.danger },
  badge: { fontSize: 10, paddingHorizontal: 4, paddingVertical: 1, borderRadius: 2, overflow: 'hidden' },
  tabs: { flexDirection: 'row', backgroundColor: 'white', borderBottomWidth: 1, borderBottomColor: C.border },
  tab: { flex: 1, paddingVertical: 12, alignItems: 'center', borderBottomWidth: 2, borderBottomColor: 'transparent' },
  tabActive: { borderBottomColor: C.primary },
  tabText: { fontSize: 13, color: C.text },
  product: { flex: 1, backgroundColor: 'white', margin: 4, borderRadius: 2, overflow: 'hidden' },
  discount: { position: 'absolute', top: 0, right: 0, backgroundColor: '#ffd839', color: C.primary, fontSize: 11, fontWeight: '600', paddingHorizontal: 4, paddingVertical: 2 },
  fav: { backgroundColor: C.primary, color: 'white', fontSize: 10 },
  mall: { backgroundColor: '#d0011b', color: 'white', fontSize: 10 },
  divider: { height: 1, backgroundColor: C.border, marginVertical: 10 },
  bottomBar: { flexDirection: 'row', alignItems: 'center', backgroundColor: 'white', borderTopWidth: 1, borderTopColor: C.border, padding: 10, gap: 10 }
})
