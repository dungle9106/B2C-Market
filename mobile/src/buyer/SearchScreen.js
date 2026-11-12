import React, { useEffect, useRef, useState } from 'react'
import { FlatList, Image, Pressable, ScrollView, Text, TextInput, View } from 'react-native'
import * as ImagePicker from 'expo-image-picker'
import { get, upload, errorText } from '../api'
import { Button, C, Empty, Loading, ProductCard, Row, styles, Tabs } from '../ui'

const SORTS = [
  { key: 'relevance', label: 'Liên quan' },
  { key: 'newest', label: 'Mới nhất' },
  { key: 'bestselling', label: 'Bán chạy' },
  { key: 'price_asc', label: 'Giá ↑' },
  { key: 'price_desc', label: 'Giá ↓' }
]

export default function SearchScreen({ navigation, route }) {
  const categoryId = route.params?.categoryId
  const [text, setText] = useState(route.params?.q || '')
  const [q, setQ] = useState(route.params?.q || '')
  const [suggest, setSuggest] = useState(null)
  const [sort, setSort] = useState(categoryId ? 'bestselling' : 'relevance')
  const [mall, setMall] = useState(!!route.params?.mall)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const timer = useRef(null)
  const searching = !!(q || categoryId || mall)

  useEffect(() => {
    if (route.params?.title) navigation.setOptions({ title: route.params.title })
  }, [navigation, route.params?.title])

  // keyword suggestions while typing
  useEffect(() => {
    clearTimeout(timer.current)
    if (!text.trim() || text === q) { setSuggest(null); return }
    timer.current = setTimeout(() => get('/products/suggest', { q: text }).then(setSuggest).catch(() => {}), 250)
  }, [text, q])

  useEffect(() => {
    if (!searching) return
    setResult(null)
    get('/products/search', { q: q || undefined, categoryId, sort, mall: mall || undefined, size: 40 })
      .then(r => { setResult(r); setError(null) })
      .catch(e => setError(errorText(e)))
  }, [q, sort, categoryId, mall, searching])

  const submit = (value = text) => { setText(value); setQ(value.trim()); setSuggest(null) }

  return (
    <View style={styles.page}>
      <Row style={{ backgroundColor: 'white', padding: 8, gap: 8 }}>
        <TextInput value={text} onChangeText={setText} onSubmitEditing={() => submit()} autoFocus={!categoryId && !route.params?.mall} returnKeyType="search"
                   placeholder="Tìm sản phẩm" placeholderTextColor={C.faint} style={[styles.input, { flex: 1, borderColor: C.primary }]} />
        <Pressable onPress={() => navigation.navigate('ImageSearch')} hitSlop={8}><Text style={{ fontSize: 22 }}>📷</Text></Pressable>
      </Row>
      {suggest ? (
        <View style={{ backgroundColor: 'white' }}>
          {suggest.keywords.map(k => (
            <Pressable key={k} onPress={() => submit(k)} style={{ padding: 12, borderTopWidth: 1, borderTopColor: C.border }}><Text>🔍  {k}</Text></Pressable>
          ))}
        </View>
      ) : null}
      {searching ? (
        <>
          <Tabs items={SORTS} value={sort} onChange={setSort} />
          {q || categoryId ? (
            <Pressable onPress={() => setMall(m => !m)} style={{ backgroundColor: 'white', paddingHorizontal: 12, paddingBottom: 8 }}>
              <Text style={{ color: mall ? C.primary : C.muted }}>{mall ? '☑' : '☐'} Chỉ Shoppi Mall</Text>
            </Pressable>
          ) : null}
          {!result ? (error ? <Empty error={error} /> : <Loading />) : (
            <FlatList data={result.items} numColumns={2} keyExtractor={p => String(p.id)} contentContainerStyle={{ padding: 4 }}
                      ListEmptyComponent={<Empty text="Không tìm thấy sản phẩm nào. Thử từ khóa khác hoặc tìm bằng hình ảnh 📷" />}
                      renderItem={({ item }) => <ProductCard p={item} onPress={() => navigation.navigate('Product', { id: item.id })} />} />
          )}
        </>
      ) : null}
    </View>
  )
}

// "Tìm bằng hình ảnh": take or pick a photo, the backend matches colours/shapes (+ AI description when configured)
export function ImageSearchScreen({ navigation }) {
  const [photo, setPhoto] = useState(null)
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function pick(camera) {
    setError('')
    const perm = camera ? await ImagePicker.requestCameraPermissionsAsync() : await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (!perm.granted) { setError('Bạn cần cho phép truy cập ' + (camera ? 'camera' : 'thư viện ảnh')); return }
    const opts = { mediaTypes: ['images'], quality: 0.7, allowsEditing: true }
    const r = camera ? await ImagePicker.launchCameraAsync(opts) : await ImagePicker.launchImageLibraryAsync(opts)
    if (r.canceled) return
    const img = r.assets[0]
    setPhoto(img)
    setResult(null)
    setBusy(true)
    try {
      setResult(await upload('/products/image-search', img, { limit: 30 }))
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  const items = result?.items || []
  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 24 }}>
      <View style={[styles.card, { alignItems: 'center' }]}>
        {photo ? <Image source={{ uri: photo.uri }} style={{ width: 160, height: 160, borderRadius: 4 }} />
          : <Text style={[styles.muted, { textAlign: 'center', marginVertical: 20 }]}>Chụp hoặc chọn ảnh sản phẩm bạn muốn tìm. Shoppi sẽ tìm các sản phẩm có màu sắc, hình dáng giống nhất.</Text>}
        <Row style={{ gap: 10, marginTop: 12 }}>
          <Button title="📷 Chụp ảnh" onPress={() => pick(true)} disabled={busy} />
          <Button title="🖼 Chọn từ thư viện" kind="ghost" onPress={() => pick(false)} disabled={busy} />
        </Row>
        {error ? <Text style={{ color: C.danger, marginTop: 10 }}>{error}</Text> : null}
      </View>
      {busy ? <Loading /> : null}
      {result ? (
        <>
          {result.description ? <Text style={[styles.card, styles.muted]}>AI nhận diện: {result.description}</Text> : null}
          <Text style={[styles.h2, { paddingHorizontal: 12 }]}>{items.length} sản phẩm tương tự</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', paddingHorizontal: 4 }}>
            {items.map(({ product, similarity }) => (
              <View key={product.id} style={{ width: '50%' }}>
                <ProductCard p={product} onPress={() => navigation.navigate('Product', { id: product.id })} />
                <Text style={{ position: 'absolute', left: 8, top: 8, backgroundColor: 'rgba(0,0,0,.55)', color: 'white', fontSize: 11, paddingHorizontal: 4 }}>
                  Giống {Math.round((similarity || 0) * 100)}%
                </Text>
              </View>
            ))}
          </View>
          {!items.length ? <Empty text="Chưa tìm thấy sản phẩm giống ảnh này" /> : null}
        </>
      ) : null}
    </ScrollView>
  )
}
