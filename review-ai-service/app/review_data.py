"""Labelled Vietnamese reviews for the review classifier (app/model.py).

  base_set()   ~2000 generated reviews: each one is assembled from phrases of real-life complaints and praise
               (with the ways people actually type: no accents, "ko", "sp", "dc"...), so the labels are exact.
               It is the starting knowledge; the reviews sellers correct in the review hub are added on top.
  TEST         hand-written reviews, worded differently from the phrases below: the honest test of whether the
               model understands new sentences, not only the ones it was built from.

Issue keys: lexicon.ISSUES. Sentiment: POSITIVE / NEUTRAL / NEGATIVE.
"""
import random
import unicodedata

COMPLAINTS = {
    "QUALITY": ["hàng bị lỗi", "dùng được 2 ngày thì hỏng", "chất liệu mỏng, không bền", "đường may cẩu thả, chỉ thừa nhiều",
                "giặt một lần đã phai màu", "máy chạy được ít hôm là hư", "pin tụt rất nhanh", "vải xù lông sau vài lần mặc",
                "mở ra đã thấy vết nứt", "chất lượng kém hơn mong đợi", "sản phẩm có mùi hôi khó chịu", "bấm nút không ăn",
                "sạc không vào điện", "đồ ăn bị mốc", "sản phẩm đã hết hạn sử dụng", "quai túi bị bung chỉ", "đế giày bong keo"],
    "WRONG_DESCRIPTION": ["màu thực tế khác hẳn trên hình", "không giống ảnh shop đăng", "mô tả một đằng giao một nẻo",
                          "chất liệu không đúng như mô tả", "ảnh quảng cáo đẹp hơn thực tế nhiều", "ghi cotton mà sờ như vải nilon",
                          "nhận về khác xa hình", "thông số không đúng như shop ghi"],
    "DELIVERY": ["giao hàng quá chậm, hơn 1 tuần mới tới", "đơn vị vận chuyển để hàng lâu", "shipper thái độ khó chịu",
                 "đợi mãi mới nhận được hàng", "giao trễ hẹn mấy ngày", "ship chậm kinh khủng", "shipper không gọi trước đã hoàn hàng",
                 "hàng đi vòng vèo cả tuần"],
    "PACKAGING": ["hộp móp méo hết", "đóng gói sơ sài, không có chống sốc", "thùng bị rách khi nhận", "chai bị vỡ do gói không cẩn thận",
                  "gói hàng ẩu", "hàng chỉ bọc mỗi túi nilon", "hộp bị bẹp một góc"],
    "SIZE": ["size bị nhỏ so với bảng", "mặc bị chật dù chọn đúng size", "áo rộng thùng thình", "giày bị chật mũi",
             "quần dài quá phải đi sửa", "form nhỏ, nên lấy lên 1 size", "size lệch so với bảng size của shop"],
    "MISSING_ITEM": ["giao thiếu 1 món", "thiếu phụ kiện đi kèm", "đặt màu đen giao màu trắng", "gửi nhầm sản phẩm khác",
                     "trong hộp không có sạc", "đặt 3 cái chỉ nhận được 2", "thiếu quà tặng như quảng cáo"],
    "AUTHENTICITY": ["nghi là hàng giả", "không phải hàng chính hãng", "bôi lên bị nổi mẩn đỏ", "dùng xong bị kích ứng da",
                     "mùi hắc rất khó chịu, sợ độc hại", "tem chống hàng giả bị bóc", "hàng nhái, không có mã vạch"],
    "PRICE": ["giá hơi cao so với chất lượng", "không đáng số tiền bỏ ra", "đắt hơn ngoài tiệm", "giá này mua chỗ khác được hàng tốt hơn",
              "mắc quá so với hàng nhận được"],
    "SERVICE": ["nhắn shop không trả lời", "shop không hỗ trợ đổi trả", "tư vấn sai size", "đã trả hàng mà chưa được hoàn tiền",
                "thái độ chủ shop rất tệ", "hẹn gửi bù mà không gửi", "shop chặn tin nhắn khi mình khiếu nại"],
}
PRAISE = ["hàng đẹp, chất lượng tốt", "vải mềm mặc mát", "giao hàng nhanh", "đóng gói cẩn thận", "giống hình 100%",
          "shop tư vấn nhiệt tình", "giá hợp lý", "mặc vừa in", "sẽ ủng hộ shop dài dài", "đáng đồng tiền", "dùng rất thích",
          "pin trâu", "máy chạy êm", "thơm lâu", "màu xinh", "hàng chính hãng có tem", "đóng gói kỹ có chống sốc", "rất hài lòng",
          "10 điểm không có nhưng", "mua lần 2 vẫn ổn", "chất lượng vượt mong đợi", "đúng mô tả", "shipper thân thiện"]
NEUTRAL = ["tạm được", "cũng bình thường", "chưa dùng nên chưa biết", "được cái giá rẻ", "dùng thử xem sao", "hàng ổn so với giá",
           "không có gì đặc biệt"]
NEGATIVE_END = ["rất thất vọng", "không mua lại nữa", "mọi người cân nhắc nhé", "phí tiền", "chán thật sự", "lần đầu mua mà buồn ghê"]

# how people type: the same words, shortened
TEEN = [("không", "ko"), ("không", "k"), ("được", "dc"), ("được", "đc"), ("sản phẩm", "sp"), ("quá", "wá"), ("rồi", "r"),
        ("mình", "mk"), ("nhanh", "nhanh nhanh"), ("hàng", "hàg")]


def _plain(text):
    t = unicodedata.normalize("NFD", text.replace("đ", "d").replace("Đ", "D"))
    return "".join(c for c in t if unicodedata.category(c) != "Mn")


def _typed(text, rng):
    for full, short in TEEN:
        if full in text and rng.random() < 0.35:
            text = text.replace(full, short)
    if rng.random() < 0.25:
        text = _plain(text)  # typed without accents
    return text if rng.random() < 0.5 else text[0].upper() + text[1:]


def _join(parts, rng):
    sep = rng.choice([", ", ". ", ", nhưng ", " mà ", ". Tuy nhiên "])
    return rng.choice([", ", ". "]).join(parts[:-1]) + (sep if len(parts) > 1 else "") + parts[-1]


def base_set(n=2000, seed=11):
    rng = random.Random(seed)
    out = []
    keys = list(COMPLAINTS)
    for _ in range(n):
        kind = rng.choices(["happy", "mixed", "neutral", "angry"], weights=[40, 20, 10, 30])[0]
        issues, parts = [], []
        if kind == "happy":
            rating = rng.choice([5, 5, 5, 4])
            parts = rng.sample(PRAISE, rng.randint(1, 3))
            sentiment = "POSITIVE"
        elif kind == "mixed":
            rating = rng.choice([4, 3, 3])
            k = rng.choice(keys)
            issues = [k]
            parts = [rng.choice(PRAISE), rng.choice(COMPLAINTS[k])]
            rng.shuffle(parts)
            sentiment = "NEUTRAL"
        elif kind == "neutral":
            rating = 3
            parts = [rng.choice(NEUTRAL)] + ([rng.choice(NEUTRAL)] if rng.random() < 0.4 else [])
            sentiment = "NEUTRAL"
        else:
            rating = rng.choice([1, 1, 2])
            issues = rng.sample(keys, rng.choice([1, 1, 1, 2]))
            parts = [rng.choice(COMPLAINTS[k]) for k in issues]
            if rng.random() < 0.6:
                parts.append(rng.choice(NEGATIVE_END))
            sentiment = "NEGATIVE"
        out.append({"rating": rating, "content": _typed(_join(parts, rng), rng), "issues": issues, "sentiment": sentiment})
    return out


# (stars, issues, sentiment, text): written by hand with other words than the phrases above
TEST = [
    (5, "", "POSITIVE", "Áo mặc lên form đẹp lắm, chất vải dày dặn, đi chơi ai cũng khen"),
    (5, "", "POSITIVE", "Nhận hàng sau 2 ngày, sp y như ảnh, shop còn tặng kèm móc khoá dễ thương"),
    (5, "", "POSITIVE", "Tai nghe nghe nhạc bass ấm, kết nối nhanh, xài cả ngày chưa hết pin"),
    (4, "", "POSITIVE", "Kem dưỡng thấm nhanh, không bết dính, dùng một tuần da mịn hơn hẳn"),
    (5, "", "POSITIVE", "Nồi chiên nấu ngon, dễ vệ sinh, mẹ mình rất ưng"),
    (5, "", "POSITIVE", "Mua tặng bạn gái, bạn ấy thích lắm. Cảm ơn shop nhiều"),
    (4, "", "POSITIVE", "Giày êm chân, đi bộ cả buổi không đau, màu ngoài đời còn đẹp hơn ảnh"),
    (5, "", "POSITIVE", "Shop rep tin nhắn nhanh, chọn giúp mình size chuẩn luôn"),
    (5, "", "POSITIVE", "hang ve nhanh, goi ky, dung on lam nha moi nguoi"),
    (5, "", "POSITIVE", "Lần thứ 3 quay lại mua, chất lượng vẫn ổn định như cũ"),
    (3, "", "NEUTRAL", "Cũng tạm, không xuất sắc nhưng với giá này thì chấp nhận được"),
    (3, "", "NEUTRAL", "Mới nhận, chưa xài nên chưa đánh giá được nhiều"),
    (3, "", "NEUTRAL", "Bình thường thôi, giống mấy chỗ khác bán"),
    (1, "QUALITY", "NEGATIVE", "Quạt quay được ba hôm thì kêu rè rè rồi đứng luôn"),
    (1, "QUALITY", "NEGATIVE", "Mặc đi làm đúng một lần mà đường chỉ ở nách bung hết"),
    (2, "QUALITY", "NEGATIVE", "Bình giữ nhiệt mà để nước nóng 1 tiếng đã nguội ngắt"),
    (1, "QUALITY", "NEGATIVE", "Bánh mở ra có đốm xanh, ăn không được"),
    (2, "QUALITY", "NEGATIVE", "Son lên môi bị vón cục, mau trôi"),
    (1, "QUALITY", "NEGATIVE", "dien thoai sac ko vao, bat len la tu tat"),
    (2, "WRONG_DESCRIPTION", "NEGATIVE", "Hình đăng màu be mà nhận về màu vàng chanh, khác một trời một vực"),
    (1, "WRONG_DESCRIPTION", "NEGATIVE", "Quảng cáo da thật nhưng thực ra là da PU bóc ra từng mảng"),
    (2, "WRONG_DESCRIPTION", "NEGATIVE", "Ghi dung tích 1 lít mà đong ra chưa tới 800ml"),
    (2, "DELIVERY", "NEGATIVE", "Đặt từ đầu tháng tới giữa tháng mới thấy hàng"),
    (2, "DELIVERY", "NEGATIVE", "Anh giao hàng gọi điện quát tháo, bảo không xuống lấy thì trả về"),
    (3, "DELIVERY", "NEUTRAL", "Sản phẩm dùng được, có điều vận chuyển hơi lâu, chờ gần 10 ngày"),
    (1, "DELIVERY", "NEGATIVE", "Bên vận chuyển báo đã giao nhưng mình chưa hề nhận được gì"),
    (2, "PACKAGING", "NEGATIVE", "Thùng giấy nát bươm, ly bên trong mẻ mất hai cái"),
    (3, "PACKAGING", "NEUTRAL", "Hàng ok nhưng shop gói sơ quá, mỗi lớp giấy báo mỏng dính"),
    (2, "PACKAGING", "NEGATIVE", "Lọ nước hoa đổ tràn ra ngoài vì nắp không được quấn băng keo"),
    (2, "SIZE", "NEGATIVE", "Mình 50kg lấy size M theo tư vấn mà mặc như áo trẻ con"),
    (3, "SIZE", "NEUTRAL", "Váy xinh nhưng phần eo bó quá, ngồi xuống khó chịu"),
    (2, "SIZE", "NEGATIVE", "Dép 38 mà đi như 36, cấn hết ngón chân"),
    (2, "MISSING_ITEM", "NEGATIVE", "Mua combo 2 hộp, mở thùng ra chỉ có một"),
    (1, "MISSING_ITEM", "NEGATIVE", "Đặt bản màu xanh mà gửi bản màu hồng, không ai báo trước"),
    (2, "MISSING_ITEM", "NEGATIVE", "Máy về không có dây cáp, hỏi thì shop bảo bán riêng"),
    (1, "AUTHENTICITY", "NEGATIVE", "Xịt thử mùi cồn nồng nặc, chắc chắn không phải hàng auth"),
    (1, "AUTHENTICITY", "NEGATIVE", "Thoa kem buổi tối, sáng dậy mặt đỏ rát và ngứa"),
    (1, "AUTHENTICITY", "NEGATIVE", "Mã QR quét ra trang lạ, hàng fake rõ ràng"),
    (3, "PRICE", "NEUTRAL", "Chất lượng được nhưng giá như vậy là chát, sale mới nên mua"),
    (2, "PRICE", "NEGATIVE", "Bỏ ra gần nửa triệu cho món này thật sự hối hận"),
    (2, "SERVICE", "NEGATIVE", "Khiếu nại cả tuần không thấy shop hồi âm"),
    (1, "SERVICE", "NEGATIVE", "Gửi trả lại hàng đã 2 tuần mà vẫn chưa thấy tiền về tài khoản"),
    (2, "SERVICE", "NEGATIVE", "Hỏi size thì shop trả lời cộc lốc, xong tư vấn sai"),
    (1, "QUALITY,PACKAGING", "NEGATIVE", "Hộp bị đè bẹp, mở ra thì đồ chơi gãy luôn một chân"),
    (1, "DELIVERY,SERVICE", "NEGATIVE", "Giao chậm cả tuần, nhắn hỏi shop thì không ai trả lời"),
    (2, "SIZE,SERVICE", "NEGATIVE", "Áo chật, muốn đổi size mà shop không cho đổi"),
    (1, "WRONG_DESCRIPTION,QUALITY", "NEGATIVE", "Khác hình hoàn toàn, vải thì mỏng như giấy, mặc một lần là rách"),
    (2, "MISSING_ITEM,PACKAGING", "NEGATIVE", "Túi rách toác, bên trong rơi mất một chiếc vớ"),
    (4, "DELIVERY", "NEUTRAL", "Đồ đẹp, mỗi tội giao hơi chậm so với dự kiến"),
    (4, "PACKAGING", "NEUTRAL", "Sản phẩm tốt, hộp hơi móp một chút do vận chuyển"),
    (4, "SIZE", "NEUTRAL", "Chất đẹp nhưng size hơi rộng, mọi người nên lấy nhỏ xuống một size"),
    (3, "QUALITY", "NEUTRAL", "Nhìn thì đẹp nhưng cầm lên thấy nhẹ, cảm giác không chắc chắn"),
    (1, "QUALITY,AUTHENTICITY", "NEGATIVE", "ổ cắm cắm vào là chập điện, nguy hiểm quá"),
    (2, "QUALITY", "NEGATIVE", "May mặc xong mới thấy lỗ thủng ở tay áo"),
]


def test_set():
    return [{"rating": r, "issues": [k for k in i.split(",") if k], "sentiment": s, "content": t} for r, i, s, t in TEST]
