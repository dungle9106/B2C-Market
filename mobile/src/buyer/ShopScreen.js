import React, { useEffect, useState } from 'react'
import { Alert, FlatList, Pressable, ScrollView, Text, View } from 'react-native'
import { get, post, del, errorText } from '../api'
import { compact } from '../format'
import { Button, C, Empty, Img, Loading, ProductCard, Row, styles, Tabs } from '../ui'
import { Ticket } from './AccountScreens'

const SORTS = [{ key: 'bestselling', label: 'Bán chạy' }, { key: 'newest', label: 'Mới nhất' }, { key: 'price_asc', label: 'Giá ↑' }, { key: 'price_desc', label: 'Giá ↓' }]

export default function ShopScreen({ route, navigation }) {
  const { id } = route.params
  const [shop, setShop] = useState(null)
  const [follow, setFollow] = useState({ count: 0, following: false })
  const [sort, setSort] = useState('bestselling')
  const [items, setItems] = useState(null)
  const [error, setError] = useState(null)
  // as on the web shop page: the shop's own vouchers and categories
  const [vouchers, setVouchers] = useState([])
  const [saved, setSaved] = useState(new Set())
  const [cats, setCats] = useState([])
  const [cat, setCat] = useState(null)

  useEffect(() => {
    get(`/shops/${id}`).then(s => { setShop(s); navigation.setOptions({ title: s.name }) }).catch(e => setError(errorText(e)))
    get(`/shops/${id}/followers`).then(setFollow).catch(() => {})
    get(`/shops/${id}/vouchers`).then(setVouchers).catch(() => {})
    get(`/shops/${id}/categories`).then(setCats).catch(() => {})
  }, [id, navigation])

  useEffect(() => {
    setItems(null)
    get('/products/search', { shopId: id, shopCategoryId: cat || undefined, sort, size: 40 }).then(r => setItems(r.items)).catch(e => setError(errorText(e)))
  }, [id, sort, cat])

  async function saveVoucher(v) {
    try {
      await post(`/buyer/voucher-wallet/${v.id}`)
      setSaved(x => new Set(x).add(v.id))
    } catch (e) { Alert.alert('Không lưu được', errorText(e)) }
  }

  async function toggleFollow() {
    try {
      if (follow.following) await del(`/buyer/follows/${id}`)
      else await post(`/buyer/follows/${id}`)
      setFollow(await get(`/shops/${id}/followers`))
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    }
  }

  async function chat() {
    try {
      const c = await post('/buyer/chats', { shopId: id })
      navigation.navigate('ChatThread', { id: c.id, title: shop.name })
    } catch (e) {
      Alert.alert('Không mở được chat', errorText(e))
    }
  }

  if (!shop) return error ? <Empty error={error} /> : <Loading />
  const header = (
    <View>
      <View style={{ backgroundColor: '#5a4a42', padding: 16 }}>
        <Row style={{ gap: 12 }}>
          <Img uri={shop.logoUrl} style={{ width: 60, height: 60, borderRadius: 30, borderWidth: 2, borderColor: 'white' }} />
          <View style={{ flex: 1 }}>
            <Text style={{ color: 'white', fontSize: 17, fontWeight: '600' }}>{shop.name}</Text>
            {shop.mall ? <Text style={[styles.mall, { alignSelf: 'flex-start', marginTop: 4, paddingHorizontal: 4 }]}>Shoppi Mall</Text>
              : shop.favorite ? <Text style={[styles.fav, { alignSelf: 'flex-start', marginTop: 4, paddingHorizontal: 4 }]}>Shop Yêu thích</Text> : null}
            <Text style={{ color: '#eee', fontSize: 12, marginTop: 4 }}>{shop.province || ''}</Text>
          </View>
        </Row>
        <Row style={{ gap: 10, marginTop: 12 }}>
          <Button small title={follow.following ? '✓ Đang theo dõi' : '+ Theo dõi'} kind={follow.following ? 'ghost' : 'primary'} onPress={toggleFollow} style={{ flex: 1 }} />
          <Button small title="💬 Chat" kind="ghost" onPress={chat} style={{ flex: 1 }} />
        </Row>
      </View>
      <Row style={{ backgroundColor: 'white', justifyContent: 'space-around', paddingVertical: 12, marginBottom: 8 }}>
        <Stat label="Sản phẩm" value={shop.productCount} />
        <Stat label="Người theo dõi" value={compact(follow.count)} />
        <Stat label="Đánh giá" value={shop.ratingAvg ? `${shop.ratingAvg.toFixed(1)} (${shop.ratingCount})` : '—'} />
        <Stat label="Phản hồi chat" value={shop.chatResponseRate != null ? `${Math.round(shop.chatResponseRate)}%` : '—'} />
      </Row>
      {follow.followerVouchers?.length ? (
        <Text style={[styles.card, { color: C.primary }]}>🎁 Theo dõi shop để nhận {follow.followerVouchers.length} voucher dành riêng cho người theo dõi</Text>
      ) : null}
      {vouchers.map(v => (
        <Ticket key={v.id} v={v}>
          <Button small title={saved.has(v.id) ? 'Đã lưu' : 'Lưu'} disabled={saved.has(v.id)} onPress={() => saveVoucher(v)} />
        </Ticket>
      ))}
      {cats.length ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ backgroundColor: 'white', marginTop: 8 }} contentContainerStyle={{ padding: 8, gap: 6 }}>
          {[{ id: null, name: 'Tất cả' }, ...cats].map(c => (
            <Pressable key={String(c.id)} onPress={() => setCat(c.id)}
                       style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 14, borderWidth: 1, borderColor: cat === c.id ? C.primary : C.border }}>
              <Text style={{ color: cat === c.id ? C.primary : C.text }}>{c.name}{c.productCount != null ? ` (${c.productCount})` : ''}</Text>
            </Pressable>
          ))}
        </ScrollView>
      ) : null}
      <Tabs items={SORTS} value={sort} onChange={setSort} />
    </View>
  )
  return (
    <FlatList style={styles.page} data={items || []} numColumns={2} keyExtractor={p => String(p.id)} contentContainerStyle={{ padding: 4 }}
              ListHeaderComponent={header}
              ListEmptyComponent={items ? <Empty text="Shop chưa có sản phẩm" /> : <Loading />}
              renderItem={({ item }) => <ProductCard p={item} onPress={() => navigation.push('Product', { id: item.id })} />} />
  )
}

const Stat = ({ label, value }) => (
  <View style={{ alignItems: 'center' }}>
    <Text style={{ color: C.primary, fontWeight: '600' }}>{value}</Text>
    <Text style={{ fontSize: 11, color: C.muted }}>{label}</Text>
  </View>
)
