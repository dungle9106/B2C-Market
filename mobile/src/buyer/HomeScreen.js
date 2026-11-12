import React, { useCallback, useEffect, useState } from 'react'
import { FlatList, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { get, errorText } from '../api'
import { formatVnd } from '../format'
import { C, Empty, Img, ProductCard, Row, styles } from '../ui'

const ICONS = ['👕', '📱', '🏠', '💄', '⚽', '🍜', '📚', '🧸', '🚗', '💊']
const SHORTCUTS = [
  ['🏬', 'Shoppi Mall', ['Search', { mall: true, title: 'Shoppi Mall' }]],
  ['🪙', 'Điểm danh nhận Xu', ['Coins']],
  ['🎟', 'Kho Voucher', ['VoucherWallet']]
]

export function SearchBar({ navigation }) {
  const { top } = useSafeAreaInsets()
  return (
    <Row style={{ backgroundColor: C.primary, paddingHorizontal: 10, paddingTop: top + 8, paddingBottom: 10, gap: 10 }}>
      <Pressable onPress={() => navigation.navigate('Search')} style={{ flex: 1, backgroundColor: 'white', borderRadius: 2, padding: 9 }}>
        <Text style={{ color: C.primary }}>🔍 <Text style={{ color: C.faint }}>Tìm sản phẩm trên Shoppi</Text></Text>
      </Pressable>
      <Pressable onPress={() => navigation.navigate('ImageSearch')} hitSlop={8}><Text style={{ fontSize: 22 }}>📷</Text></Pressable>
      <Pressable onPress={() => navigation.navigate('Cart')} hitSlop={8}><Text style={{ fontSize: 22 }}>🛒</Text></Pressable>
    </Row>
  )
}

export default function HomeScreen({ navigation }) {
  const [categories, setCategories] = useState([])
  const [flash, setFlash] = useState([])
  const [items, setItems] = useState([])
  const [page, setPage] = useState({ page: -1, totalPages: 1 })
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState(null)

  const loadMore = useCallback(async (reset = false) => {
    if (loading || (!reset && page.page + 1 >= page.totalPages)) return
    setLoading(true)
    try {
      const next = reset ? 0 : page.page + 1
      const res = await get('/products/search', { sort: 'bestselling', page: next, size: 20 })
      setItems(old => (reset ? res.items : [...old, ...res.items]))
      setPage({ page: res.page, totalPages: res.totalPages })
      setError(null)
    } catch (e) {
      setError(errorText(e))
    } finally {
      setLoading(false)
    }
  }, [loading, page])

  const loadAll = useCallback(async () => {
    setRefreshing(true)
    await Promise.all([
      get('/categories/tree').then(setCategories).catch(() => {}),
      get('/products/flash-sale', { limit: 12 }).then(setFlash).catch(() => {}),
      loadMore(true)
    ])
    setRefreshing(false)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => { loadAll() }, [loadAll])

  const header = (
    <View>
      <Row style={{ backgroundColor: 'white', marginBottom: 8, paddingVertical: 10 }}>
        {SHORTCUTS.map(([glyph, label, target]) => (
          <Pressable key={label} onPress={() => navigation.navigate(...target)} style={{ flex: 1, alignItems: 'center' }}>
            <Text style={{ fontSize: 22 }}>{glyph}</Text>
            <Text style={{ fontSize: 11, marginTop: 4 }}>{label}</Text>
          </Pressable>
        ))}
      </Row>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ backgroundColor: 'white', marginBottom: 8 }}
                  contentContainerStyle={{ padding: 10, gap: 6 }}>
        {categories.map((c, i) => (
          <Pressable key={c.id} onPress={() => navigation.navigate('Search', { categoryId: c.id, title: c.name })} style={{ width: 74, alignItems: 'center' }}>
            <View style={{ width: 48, height: 48, borderRadius: 24, backgroundColor: C.primarySoft, alignItems: 'center', justifyContent: 'center' }}>
              <Text style={{ fontSize: 22 }}>{ICONS[i % ICONS.length]}</Text>
            </View>
            <Text numberOfLines={2} style={{ fontSize: 11, textAlign: 'center', marginTop: 4 }}>{c.name}</Text>
          </Pressable>
        ))}
      </ScrollView>
      {flash.length ? (
        <View style={{ backgroundColor: 'white', marginBottom: 8, paddingVertical: 10 }}>
          <Text style={{ color: C.primary, fontWeight: '700', fontSize: 16, paddingHorizontal: 12, marginBottom: 8 }}>⚡ FLASH SALE</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 8, gap: 8 }}>
            {flash.map(p => (
              <Pressable key={p.id} onPress={() => navigation.navigate('Product', { id: p.id })} style={{ width: 110 }}>
                <Img uri={p.thumbnailUrl} style={{ width: 110, height: 110 }} />
                <Text style={{ color: C.primary, fontWeight: '600', textAlign: 'center', marginTop: 4 }}>{formatVnd(p.price)}</Text>
                {p.flashSaleStock ? (
                  <Text style={{ fontSize: 11, textAlign: 'center', color: 'white', backgroundColor: '#f69113', borderRadius: 8, overflow: 'hidden', marginTop: 2 }}>
                    Đã bán {p.flashSaleSold || 0}/{p.flashSaleStock}
                  </Text>
                ) : null}
              </Pressable>
            ))}
          </ScrollView>
        </View>
      ) : null}
      <Text style={{ backgroundColor: 'white', color: C.primary, textAlign: 'center', padding: 12, fontWeight: '600', borderBottomWidth: 3, borderBottomColor: C.primary }}>
        GỢI Ý HÔM NAY
      </Text>
    </View>
  )

  return (
    <View style={styles.page}>
      <SearchBar navigation={navigation} />
      <FlatList data={items} numColumns={2} keyExtractor={p => String(p.id)} contentContainerStyle={{ padding: 4 }}
                ListHeaderComponent={header}
                ListEmptyComponent={refreshing ? null : <Empty text="Chưa có sản phẩm" error={error} onRetry={loadAll} />}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={loadAll} />}
                onEndReached={() => loadMore()} onEndReachedThreshold={0.5}
                renderItem={({ item }) => <ProductCard p={item} onPress={() => navigation.navigate('Product', { id: item.id })} />} />
    </View>
  )
}
