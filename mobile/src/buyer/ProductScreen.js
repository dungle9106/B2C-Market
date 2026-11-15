import React, { useEffect, useMemo, useState } from 'react'
import { Alert, Dimensions, FlatList, Pressable, ScrollView, Text, View } from 'react-native'
import { get, post, put, del, errorText } from '../api'
import { compact, formatDateTime, formatVnd } from '../format'
import { BottomBar, Badge, Button, C, Empty, Img, Loading, ProductCard, Row, Stars, styles } from '../ui'

const W = Dimensions.get('window').width

function Chip({ text, active, disabled, onPress }) {
  return (
    <Pressable onPress={onPress} disabled={disabled}
               style={{ borderWidth: 1, borderColor: active ? C.primary : C.border, backgroundColor: active ? C.primarySoft : 'white',
                        paddingHorizontal: 12, paddingVertical: 6, borderRadius: 2, opacity: disabled ? 0.4 : 1 }}>
      <Text style={{ color: active ? C.primary : C.text }}>{text}</Text>
    </Pressable>
  )
}

export default function ProductScreen({ route, navigation }) {
  const { id } = route.params
  const [p, setP] = useState(null)
  const [error, setError] = useState(null)
  const [likes, setLikes] = useState({ count: 0, liked: false })
  const [watch, setWatch] = useState({ watching: false, targetPrice: null })
  const [reviews, setReviews] = useState(null)
  const [similar, setSimilar] = useState([])
  const [opt1, setOpt1] = useState(null)
  const [opt2, setOpt2] = useState(null)
  const [qty, setQty] = useState(1)
  const [busy, setBusy] = useState(false)
  const [slide, setSlide] = useState(0)

  useEffect(() => {
    get(`/products/${id}`).then(setP).catch(e => setError(errorText(e)))
    get(`/products/${id}/likes`).then(setLikes).catch(() => {})
    get(`/buyer/price-watches/${id}`).then(setWatch).catch(() => {})
    get(`/products/${id}/reviews`, { size: 3 }).then(setReviews).catch(() => {})
    get(`/products/${id}/similar`).then(setSimilar).catch(() => {})
  }, [id])

  const variants = p?.variants || []
  const options1 = useMemo(() => [...new Set(variants.map(v => v.option1).filter(Boolean))], [variants])
  const options2 = useMemo(() => [...new Set(variants.map(v => v.option2).filter(Boolean))], [variants])
  const variant = variants.length
    ? variants.find(v => (v.option1 || null) === (options1.length ? opt1 : null) && (v.option2 || null) === (options2.length ? opt2 : null))
    : null
  const stock = variant ? variant.stock : p?.stock
  const price = variant?.price ?? p?.price

  if (!p) return error ? <Empty error={error} /> : <Loading />

  async function toggleLike() {
    try {
      if (likes.liked) await del(`/buyer/likes/${id}`)
      else await post(`/buyer/likes/${id}`)
      setLikes(await get(`/products/${id}/likes`))
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    }
  }

  // "Bao khi giam gia": any drop, or only down to a price the buyer picks
  function onWatch() {
    const now = Number(p.price)
    const at = (pct) => Math.round(now * (100 - pct) / 100 / 1000) * 1000
    const save = async (target) => {
      try {
        const r = await put(`/buyer/price-watches/${id}`, { targetPrice: target })
        setWatch({ watching: true, targetPrice: r.targetPrice })
      } catch (e) {
        Alert.alert('Không thực hiện được', errorText(e))
      }
    }
    const options = [
      { text: 'Báo mọi lần giảm giá', onPress: () => save(null) },
      { text: `Khi còn ≤ ${formatVnd(at(10))} (-10%)`, onPress: () => save(at(10)) },
      { text: `Khi còn ≤ ${formatVnd(at(20))} (-20%)`, onPress: () => save(at(20)) }
    ]
    if (watch.watching) {
      options.push({ text: 'Bỏ theo dõi giá', style: 'destructive', onPress: async () => {
        try { await del(`/buyer/price-watches/${id}`); setWatch({ watching: false, targetPrice: null }) } catch (e) { Alert.alert('Không thực hiện được', errorText(e)) }
      } })
    }
    options.push({ text: 'Đóng', style: 'cancel' })
    Alert.alert('Báo khi giảm giá', `Giá hiện tại ${formatVnd(now)}. Shoppi sẽ thông báo khi giá giảm (kể cả Flash Sale).`, options)
  }

  async function addToCart(buyNow) {
    if (variants.length && !variant) { Alert.alert('Chọn phân loại', 'Vui lòng chọn phân loại hàng trước'); return }
    setBusy(true)
    try {
      const item = await post('/cart', { productId: p.id, variantId: variant?.id, quantity: qty })
      if (buyNow) navigation.navigate('Checkout', { cartItemIds: [item.id] })
      else Alert.alert('Đã thêm vào giỏ hàng', `${p.name} x${qty}`, [{ text: 'Xem giỏ hàng', onPress: () => navigation.navigate('Cart') }, { text: 'OK' }])
    } catch (e) {
      Alert.alert('Không thêm được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  async function chat() {
    try {
      const c = await post('/buyer/chats', { shopId: p.shopId })
      navigation.navigate('ChatThread', { id: c.id, title: p.shopName })
    } catch (e) {
      Alert.alert('Không mở được chat', errorText(e))
    }
  }

  const images = p.imageUrls?.length ? p.imageUrls : [p.thumbnailUrl]
  return (
    <View style={styles.page}>
      <ScrollView>
        <View>
          <FlatList data={images} horizontal pagingEnabled showsHorizontalScrollIndicator={false} keyExtractor={(u, i) => String(i)}
                    onMomentumScrollEnd={e => setSlide(Math.round(e.nativeEvent.contentOffset.x / W))}
                    renderItem={({ item }) => <Img uri={item} style={{ width: W, height: W }} />} />
          <Text style={{ position: 'absolute', right: 12, bottom: 12, backgroundColor: 'rgba(0,0,0,.4)', color: 'white', paddingHorizontal: 8, borderRadius: 10, overflow: 'hidden' }}>
            {slide + 1}/{images.length}
          </Text>
        </View>

        <View style={styles.card}>
          <Row style={{ gap: 8 }}>
            <Text style={{ color: C.primary, fontSize: 22, fontWeight: '600' }}>{formatVnd(price)}</Text>
            {p.originalPrice && p.originalPrice > price ? <Text style={{ color: C.faint, textDecorationLine: 'line-through' }}>{formatVnd(p.originalPrice)}</Text> : null}
            {p.discountPercent ? <Badge text={`-${p.discountPercent}%`} /> : null}
          </Row>
          {p.flashSale ? <Text style={{ color: C.primary, marginTop: 4 }}>⚡ Flash Sale đến {formatDateTime(p.flashSaleEnd)}</Text> : null}
          <Text style={{ fontSize: 16, marginTop: 8 }}>{p.shopMall ? <Text style={styles.mall}> Mall </Text> : p.shopFavorite ? <Text style={styles.fav}> Yêu thích </Text> : null} {p.name}</Text>
          <Row style={{ justifyContent: 'space-between', marginTop: 10 }}>
            <Row style={{ gap: 6 }}>
              <Stars value={p.ratingAvg} />
              <Text style={styles.muted}>{p.ratingAvg?.toFixed(1)} ({p.ratingCount}) | Đã bán {compact(p.soldCount)}</Text>
            </Row>
            <Pressable onPress={toggleLike} hitSlop={8}>
              <Text style={{ color: likes.liked ? C.primary : C.muted }}>{likes.liked ? '♥' : '♡'} {likes.count}</Text>
            </Pressable>
          </Row>
          <Pressable onPress={onWatch} hitSlop={6} style={{ marginTop: 8, alignSelf: 'flex-start' }}>
            <Text style={{ color: watch.watching ? C.primary : C.muted }}>
              🔔 {watch.watching ? (watch.targetPrice ? `Báo khi ≤ ${formatVnd(watch.targetPrice)}` : 'Đang theo dõi giá') : 'Báo khi giảm giá'}
            </Text>
          </Pressable>
          {p.freeShipping ? <Text style={{ color: C.green, marginTop: 8 }}>🚚 Miễn phí vận chuyển</Text> : null}
          {p.returnDays ? <Text style={[styles.muted, { marginTop: 4 }]}>↩ Đổi trả trong {p.returnDays} ngày</Text> : null}
        </View>

        {variants.length ? (
          <View style={styles.card}>
            {options1.length ? <Text style={styles.label}>{p.tier1Name || 'Phân loại'}</Text> : null}
            <Row style={{ flexWrap: 'wrap', gap: 8, marginBottom: 10 }}>
              {options1.map(o => <Chip key={o} text={o} active={opt1 === o} onPress={() => setOpt1(opt1 === o ? null : o)}
                                       disabled={!variants.some(v => v.option1 === o && v.stock > 0)} />)}
            </Row>
            {options2.length ? <Text style={styles.label}>{p.tier2Name || 'Phân loại 2'}</Text> : null}
            <Row style={{ flexWrap: 'wrap', gap: 8 }}>
              {options2.map(o => <Chip key={o} text={o} active={opt2 === o} onPress={() => setOpt2(opt2 === o ? null : o)}
                                       disabled={!variants.some(v => v.option2 === o && (!opt1 || v.option1 === opt1) && v.stock > 0)} />)}
            </Row>
          </View>
        ) : null}

        <Row style={[styles.card, { justifyContent: 'space-between' }]}>
          <Text>Số lượng <Text style={styles.muted}>({stock} sản phẩm có sẵn)</Text></Text>
          <Row>
            <Button title="−" kind="ghost" small onPress={() => setQty(Math.max(1, qty - 1))} />
            <Text style={{ width: 40, textAlign: 'center' }}>{qty}</Text>
            <Button title="+" kind="ghost" small onPress={() => setQty(Math.min(stock || 1, qty + 1))} />
          </Row>
        </Row>

        <Pressable onPress={() => navigation.navigate('Shop', { id: p.shopId })} style={[styles.card, { flexDirection: 'row', alignItems: 'center', gap: 12 }]}>
          <Img uri={p.shopLogoUrl} style={{ width: 48, height: 48, borderRadius: 24 }} />
          <View style={{ flex: 1 }}>
            <Text style={{ fontWeight: '600' }}>{p.shopName}</Text>
            <Text style={styles.muted}>{p.shopRating ? `★ ${p.shopRating.toFixed(1)} (${p.shopRatingCount} đánh giá)` : 'Chưa có đánh giá'}{p.province ? ` · ${p.province}` : ''}</Text>
          </View>
          <Text style={{ color: C.primary, borderWidth: 1, borderColor: C.primary, paddingHorizontal: 8, paddingVertical: 4 }}>Xem Shop</Text>
        </Pressable>

        <View style={styles.card}>
          <Text style={styles.h2}>Mô tả sản phẩm</Text>
          <Text style={{ lineHeight: 21 }}>{p.description || 'Chưa có mô tả'}</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.h2}>Đánh giá sản phẩm ({p.ratingCount})</Text>
          {(reviews?.items || []).map(r => (
            <View key={r.id} style={{ borderTopWidth: 1, borderTopColor: C.border, paddingVertical: 10 }}>
              <Text style={{ fontWeight: '500' }}>{r.buyerName}</Text>
              <Stars value={r.rating} />
              {r.variantName ? <Text style={styles.muted}>Phân loại: {r.variantName}</Text> : null}
              {r.comment ? <Text style={{ marginTop: 4 }}>{r.comment}</Text> : null}
              {r.imageUrls?.length ? (
                <Row style={{ gap: 6, marginTop: 6 }}>{r.imageUrls.map(u => <Img key={u} uri={u} style={{ width: 64, height: 64 }} />)}</Row>
              ) : null}
              {r.sellerReply ? <Text style={{ backgroundColor: C.bg, padding: 8, marginTop: 6, color: C.muted }}>Phản hồi của Người bán: {r.sellerReply}</Text> : null}
            </View>
          ))}
          {!reviews?.items?.length ? <Text style={styles.muted}>Chưa có đánh giá</Text> : null}
        </View>

        {similar.length ? (
          <View style={{ marginBottom: 8 }}>
            <Text style={[styles.h2, { padding: 12, marginBottom: 0 }]}>Sản phẩm tương tự</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', paddingHorizontal: 4 }}>
              {similar.slice(0, 6).map(s => (
                <View key={s.id} style={{ width: '50%' }}><ProductCard p={s} onPress={() => navigation.push('Product', { id: s.id })} /></View>
              ))}
            </View>
          </View>
        ) : null}
      </ScrollView>

      <BottomBar tight>
        <Pressable onPress={chat} style={{ flex: 1, alignItems: 'center', paddingVertical: 10, backgroundColor: C.green }}>
          <Text style={{ color: 'white' }}>💬 Chat ngay</Text>
        </Pressable>
        <Pressable onPress={() => addToCart(false)} disabled={busy || !stock} style={{ flex: 1, alignItems: 'center', paddingVertical: 10, backgroundColor: C.green, borderLeftWidth: 1, borderLeftColor: '#ffffff55' }}>
          <Text style={{ color: 'white' }}>🛒 Thêm vào giỏ</Text>
        </Pressable>
        <Pressable onPress={() => addToCart(true)} disabled={busy || !stock} style={{ flex: 1.2, alignItems: 'center', justifyContent: 'center', alignSelf: 'stretch', backgroundColor: stock ? C.primary : C.faint }}>
          <Text style={{ color: 'white', fontWeight: '600' }}>{stock ? 'Mua ngay' : 'Hết hàng'}</Text>
        </Pressable>
      </BottomBar>
    </View>
  )
}
