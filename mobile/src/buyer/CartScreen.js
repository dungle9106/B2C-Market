import React, { useMemo, useState } from 'react'
import { Alert, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native'
import { get, patch, del, errorText } from '../api'
import { formatVnd } from '../format'
import { BottomBar, Button, C, Empty, Img, Loading, Row, styles, useLoad } from '../ui'

const Check = ({ on, onPress, disabled }) => (
  <Pressable onPress={onPress} disabled={disabled} hitSlop={10}
             style={{ width: 20, height: 20, borderWidth: 1, borderColor: on ? C.primary : C.faint, backgroundColor: on ? C.primary : 'white',
                      alignItems: 'center', justifyContent: 'center', opacity: disabled ? 0.3 : 1 }}>
    {on ? <Text style={{ color: 'white', fontSize: 12 }}>✓</Text> : null}
  </Pressable>
)

export default function CartScreen({ navigation }) {
  const { data, setData, error, refreshing, refresh, reload } = useLoad(() => get('/cart'))
  const [selected, setSelected] = useState(new Set())

  const shops = useMemo(() => {
    const map = new Map()
    for (const it of data || []) {
      if (!map.has(it.shopId)) map.set(it.shopId, { shopId: it.shopId, shopName: it.shopName, items: [] })
      map.get(it.shopId).items.push(it)
    }
    return [...map.values()]
  }, [data])

  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  const chosen = data.filter(it => selected.has(it.id) && it.available)
  const total = chosen.reduce((s, it) => s + it.unitPrice * it.quantity, 0)
  const toggle = (ids, on) => setSelected(old => {
    const next = new Set(old)
    ids.forEach(id => (on ? next.add(id) : next.delete(id)))
    return next
  })

  async function setQty(it, q) {
    try {
      if (q < 1) {
        Alert.alert('Xóa sản phẩm?', it.productName, [{ text: 'Hủy' }, {
          text: 'Xóa', style: 'destructive',
          onPress: async () => { await del(`/cart/${it.id}`); toggle([it.id], false); reload() }
        }])
        return
      }
      const updated = await patch(`/cart/${it.id}`, { quantity: q })
      setData(d => d.map(x => (x.id === it.id ? updated : x)))
    } catch (e) {
      Alert.alert('Không cập nhật được', errorText(e))
    }
  }

  return (
    <View style={styles.page}>
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        {!data.length ? <Empty text="Giỏ hàng của bạn còn trống" /> : null}
        {shops.map(s => {
          const ids = s.items.filter(i => i.available).map(i => i.id)
          const all = ids.length > 0 && ids.every(id => selected.has(id))
          return (
            <View key={s.shopId} style={[styles.card, { paddingHorizontal: 12 }]}>
              <Row style={{ gap: 10, marginBottom: 6 }}>
                <Check on={all} onPress={() => toggle(ids, !all)} disabled={!ids.length} />
                <Pressable onPress={() => navigation.navigate('Shop', { id: s.shopId })}><Text style={{ fontWeight: '600' }}>🏪 {s.shopName} ›</Text></Pressable>
              </Row>
              {s.items.map(it => (
                <Row key={it.id} style={{ gap: 10, paddingVertical: 10, borderTopWidth: 1, borderTopColor: C.border, alignItems: 'flex-start' }}>
                  <View style={{ paddingTop: 30 }}><Check on={selected.has(it.id)} onPress={() => toggle([it.id], !selected.has(it.id))} disabled={!it.available} /></View>
                  <Pressable onPress={() => navigation.navigate('Product', { id: it.productId })}>
                    <Img uri={it.thumbnailUrl} style={{ width: 76, height: 76 }} />
                  </Pressable>
                  <View style={{ flex: 1 }}>
                    <Text numberOfLines={2}>{it.productName}</Text>
                    {it.variantName ? <Text style={styles.muted}>Phân loại: {it.variantName}</Text> : null}
                    {!it.available ? <Text style={{ color: C.danger, fontSize: 12 }}>Sản phẩm không còn bán / hết hàng</Text> : null}
                    <Row style={{ justifyContent: 'space-between', marginTop: 6 }}>
                      <Text style={{ color: C.primary }}>{formatVnd(it.unitPrice)}</Text>
                      <Row>
                        <Pressable onPress={() => setQty(it, it.quantity - 1)} style={qtyBtn}><Text>−</Text></Pressable>
                        <Text style={{ width: 34, textAlign: 'center' }}>{it.quantity}</Text>
                        <Pressable onPress={() => setQty(it, it.quantity + 1)} style={qtyBtn}><Text>+</Text></Pressable>
                      </Row>
                    </Row>
                  </View>
                </Row>
              ))}
            </View>
          )
        })}
      </ScrollView>
      <BottomBar>
        <View style={{ flex: 1, alignItems: 'flex-end' }}>
          <Text>Tổng thanh toán <Text style={{ color: C.primary, fontWeight: '600', fontSize: 16 }}>{formatVnd(total)}</Text></Text>
        </View>
        <Button title={`Mua hàng (${chosen.length})`} disabled={!chosen.length}
                onPress={() => navigation.navigate('Checkout', { cartItemIds: chosen.map(it => it.id) })} />
      </BottomBar>
    </View>
  )
}

const qtyBtn = { borderWidth: 1, borderColor: C.border, width: 28, height: 28, alignItems: 'center', justifyContent: 'center' }
