import React, { useRef, useState } from 'react'
import { Alert, Animated, Easing, ScrollView, Text, View } from 'react-native'
import { get, post, errorText } from '../api'
import { Button, C, Empty, Loading, styles, useLoad } from '../ui'

const SIZE = 290

// "Vong quay may man" (same rules as the web page /account/lucky-spin): the server draws the prize, the wheel
// only turns until that slot is under the pointer.
export default function LuckySpinScreen() {
  const { data, error, refresh } = useLoad(() => get('/buyer/lucky-spin'))
  const rotation = useRef(new Animated.Value(0)).current
  const angle = useRef(0)
  const [busy, setBusy] = useState(false)

  if (!data) return error ? <Empty error={error} onRetry={refresh} /> : <Loading />
  const prizes = data.prizes
  const step = 360 / prizes.length

  async function spin() {
    setBusy(true)
    try {
      const r = await post('/buyer/lucky-spin')
      const jitter = (Math.random() - 0.5) * step * 0.6
      const target = (360 - r.slot * step + jitter + 360) % 360
      const now = ((angle.current % 360) + 360) % 360
      angle.current += 360 * 6 + ((target - now + 360) % 360)
      await new Promise(done => Animated.timing(rotation, {
        toValue: angle.current, duration: 4600, easing: Easing.out(Easing.cubic), useNativeDriver: true
      }).start(done))
      Alert.alert(r.coins || r.voucherCode ? 'Chúc mừng bạn!' : 'Tiếc quá!',
        r.coins ? `Bạn nhận được ${r.label}, đã cộng vào ví Xu.` : r.voucherCode ? `Bạn nhận được ${r.label}, đã lưu vào Kho Voucher.` : 'Chúc bạn may mắn lần sau. Ngày mai quay tiếp nhé!')
      refresh()
    } catch (e) {
      Alert.alert('Không quay được', errorText(e))
      refresh()
    } finally {
      setBusy(false)
    }
  }

  const spin360 = rotation.interpolate({ inputRange: [0, 360], outputRange: ['0deg', '360deg'], extrapolate: 'extend' })
  return (
    <ScrollView style={styles.page}>
      <View style={{ backgroundColor: C.primary, alignItems: 'center', paddingVertical: 20 }}>
        <Text style={{ color: 'white', fontSize: 22, fontWeight: '700' }}>Vòng Quay May Mắn</Text>
        <Text style={{ color: 'white', opacity: 0.9, marginBottom: 14 }}>Mỗi ngày 1 lượt miễn phí</Text>
        <View style={{ width: SIZE, height: SIZE }}>
          <Animated.View style={{ width: SIZE, height: SIZE, borderRadius: SIZE / 2, backgroundColor: '#fff3e0', borderWidth: 6, borderColor: '#f5a623',
            transform: [{ rotate: spin360 }] }}>
            {prizes.map((p, i) => (
              <View key={p.code} style={{ position: 'absolute', left: 0, top: 0, width: SIZE - 12, height: SIZE - 12, alignItems: 'center',
                transform: [{ rotate: `${i * step}deg` }] }}>
                <View style={{ marginTop: 10, width: 74, paddingVertical: 4, borderRadius: 6, backgroundColor: p.color, alignItems: 'center' }}>
                  <Text style={{ fontSize: 11, fontWeight: '700', textAlign: 'center', color: '#5a2a0c' }} numberOfLines={2}>{p.label}</Text>
                </View>
              </View>
            ))}
            {prizes.map((p, i) => (
              <View key={'line' + i} style={{ position: 'absolute', left: (SIZE - 12) / 2 - 0.75, top: 0, width: 1.5, height: (SIZE - 12) / 2,
                backgroundColor: '#f5c27a', transform: [{ translateY: (SIZE - 12) / 4 }, { rotate: `${(i + 0.5) * step}deg` }, { translateY: -(SIZE - 12) / 4 }] }} />
            ))}
          </Animated.View>
          <View style={{ position: 'absolute', top: -8, left: SIZE / 2 - 12, width: 0, height: 0, borderLeftWidth: 12, borderRightWidth: 12, borderTopWidth: 26,
            borderLeftColor: 'transparent', borderRightColor: 'transparent', borderTopColor: 'white' }} />
          <View style={{ position: 'absolute', top: SIZE / 2 - 30, left: SIZE / 2 - 30, width: 60, height: 60, borderRadius: 30, backgroundColor: '#f57c00',
            borderWidth: 4, borderColor: 'white', alignItems: 'center', justifyContent: 'center' }}>
            <Text style={{ color: 'white', fontWeight: '800' }}>🎡</Text>
          </View>
        </View>
        <Text style={{ color: 'white', marginTop: 12 }}>Số dư: 🪙 {Number(data.coinBalance).toLocaleString('vi-VN')} Xu</Text>
      </View>
      <View style={{ padding: 16 }}>
        <Button title={busy ? 'Đang quay...' : data.spunToday ? `Hôm nay đã quay: ${data.todayPrize?.label || ''}` : 'QUAY NGAY'}
                disabled={busy || data.spunToday} onPress={spin} />
      </View>
      <View style={styles.card}>
        <Text style={{ fontWeight: '500', marginBottom: 6 }}>Giải thưởng</Text>
        {[...prizes].sort((a, b) => b.chance - a.chance).map(p => (
          <View key={p.code} style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 5 }}>
            <Text>{p.label}</Text><Text style={styles.muted}>{p.chance}%</Text>
          </View>
        ))}
      </View>
      {data.winners?.length ? (
        <View style={styles.card}>
          <Text style={{ fontWeight: '500', marginBottom: 6 }}>Người may mắn</Text>
          {data.winners.map((w, i) => <Text key={i} style={{ paddingVertical: 3 }}>{w.name} trúng <Text style={{ color: C.primary }}>{w.label}</Text></Text>)}
        </View>
      ) : null}
    </ScrollView>
  )
}
