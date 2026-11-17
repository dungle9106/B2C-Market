import React, { useCallback, useEffect, useRef, useState } from 'react'
import { ActivityIndicator, Alert, FlatList, Pressable, Text, View } from 'react-native'
import { useVideoPlayer, VideoView } from 'expo-video'
import { useIsFocused } from '@react-navigation/native'
import { get, post, del, fileUrl, errorText } from '../api'
import { formatVnd, compact } from '../format'
import { C, Empty, Img } from '../ui'

// "Shoppi Video" (same feed as the web page /videos): swipe up for the next clip; only the one on screen plays.
export default function VideosScreen({ navigation }) {
  const [items, setItems] = useState([])
  const [page, setPage] = useState(0)
  const [last, setLast] = useState(false)
  const [error, setError] = useState(null)
  const [active, setActive] = useState(0)
  const [height, setHeight] = useState(0)
  const loading = useRef(false)
  const focused = useIsFocused()

  const load = useCallback(async (p) => {
    if (loading.current) return
    loading.current = true
    try {
      const r = await get('/videos', { sort: 'hot', page: p, size: 6 })
      setItems(prev => (p === 0 ? r.items : [...prev, ...r.items.filter(v => !prev.some(x => x.id === v.id))]))
      setPage(p)
      setLast(p + 1 >= r.totalPages)
      setError(null)
    } catch (e) {
      setError(errorText(e))
    } finally {
      loading.current = false
    }
  }, [])
  useEffect(() => { load(0) }, [load])

  const onViewable = useRef(({ viewableItems }) => {
    if (viewableItems.length) setActive(viewableItems[0].index)
  }).current

  if (error && !items.length) return <Empty error={error} onRetry={() => load(0)} />
  return (
    <View style={{ flex: 1, backgroundColor: 'black' }} onLayout={e => setHeight(e.nativeEvent.layout.height)}>
      {!height ? null : !items.length ? (
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
          {last ? <Text style={{ color: 'white' }}>Chưa có video nào</Text> : <ActivityIndicator color="white" />}
        </View>
      ) : (
        <FlatList data={items} keyExtractor={v => String(v.id)} pagingEnabled showsVerticalScrollIndicator={false}
                  getItemLayout={(_, i) => ({ length: height, offset: height * i, index: i })}
                  onViewableItemsChanged={onViewable} viewabilityConfig={{ itemVisiblePercentThreshold: 60 }}
                  onEndReached={() => !last && load(page + 1)} onEndReachedThreshold={1.5} windowSize={3}
                  renderItem={({ item, index }) => (
                    <Clip video={item} height={height} active={focused && index === active} navigation={navigation}
                          onChange={(v) => setItems(list => list.map(x => (x.id === v.id ? v : x)))} />
                  )} />
      )}
    </View>
  )
}

function Clip({ video: v, height, active, navigation, onChange }) {
  const player = useVideoPlayer(fileUrl(v.videoUrl), p => { p.loop = true })
  const counted = useRef(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (active) {
      player.play()
      if (!counted.current) {
        counted.current = true
        post(`/videos/${v.id}/view`).catch(() => {})   // a view counts once per clip and visit
      }
    } else {
      player.pause()
    }
  }, [active, player, v.id])

  async function toggleLike() {
    setBusy(true)
    try {
      const r = v.liked ? await del(`/buyer/videos/${v.id}/like`) : await post(`/buyer/videos/${v.id}/like`)
      onChange({ ...v, liked: r.liked, likeCount: r.likeCount })
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  const product = v.products?.[0]
  return (
    <View style={{ height, backgroundColor: 'black' }}>
      <VideoView player={player} style={{ flex: 1 }} contentFit="cover" nativeControls={false} />
      <Pressable style={{ position: 'absolute', top: 0, bottom: 0, left: 0, right: 70 }}
                 onPress={() => (player.playing ? player.pause() : player.play())} />

      <View style={{ position: 'absolute', right: 10, bottom: 140, alignItems: 'center', gap: 18 }}>
        {v.shop ? (
          <Pressable onPress={() => navigation.navigate('Shop', { id: v.shop.id })}>
            <Img uri={v.shop.logoUrl || null} style={{ width: 46, height: 46, borderRadius: 23, borderWidth: 2, borderColor: 'white' }} />
          </Pressable>
        ) : null}
        <Pressable onPress={toggleLike} disabled={busy} style={{ alignItems: 'center' }}>
          <Text style={{ fontSize: 30, color: v.liked ? C.primary : 'white' }}>{v.liked ? '♥' : '♡'}</Text>
          <Text style={{ color: 'white', fontSize: 12 }}>{compact(v.likeCount)}</Text>
        </Pressable>
        <View style={{ alignItems: 'center' }}>
          <Text style={{ fontSize: 22, color: 'white' }}>▶</Text>
          <Text style={{ color: 'white', fontSize: 12 }}>{compact(v.viewCount)}</Text>
        </View>
      </View>

      <View style={{ position: 'absolute', left: 12, right: 80, bottom: 18 }}>
        {v.shop ? <Text style={{ color: 'white', fontWeight: '700', fontSize: 15 }}>@{v.shop.name}</Text> : null}
        {v.caption ? <Text style={{ color: 'white', marginTop: 4 }} numberOfLines={3}>{v.caption}</Text> : null}
        {product ? (
          <Pressable onPress={() => navigation.navigate('Product', { id: product.id })}
                     style={{ marginTop: 10, flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: 'rgba(255,255,255,0.92)', borderRadius: 6, padding: 6 }}>
            <Img uri={product.thumbnailUrl} style={{ width: 44, height: 44, borderRadius: 4 }} />
            <View style={{ flex: 1 }}>
              <Text numberOfLines={1} style={{ fontSize: 13 }}>{product.name}</Text>
              <Text style={{ color: C.primary, fontWeight: '600' }}>{formatVnd(product.price)}</Text>
            </View>
            <Text style={{ color: C.primary, fontWeight: '600' }}>Mua ›</Text>
          </Pressable>
        ) : null}
        {v.products?.length > 1 ? <Text style={{ color: 'white', fontSize: 12, marginTop: 4 }}>+{v.products.length - 1} sản phẩm khác trong video</Text> : null}
      </View>
    </View>
  )
}
