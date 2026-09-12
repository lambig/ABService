-- 作品の頒布の基準額（#349）
-- 作品が持つのは基準額1つ。経路・担い手・地域ごとの額は、作品の属性ではなく作品が誰にどこで頒布されたかの
-- 属性であり、発表を第一級にする設計（#201）が持つ。基準額は上書きされる側として作品に残る。
--
-- V8（album_distribution）を V37 で撤去した理由が、そのままここでも効く。V37 は「本来（作品 × 発表）の組に
-- 属する情報であり、1イベントで複数の作品を頒布するケースを表現できない」として落とした。経路ごとの額の列を
-- album へ置くと、同じ軸の取り違えを繰り返すことになる。
--
-- 額は自分の通貨を持つ。通貨を持たない金額が後から通貨を要求されたときに、既存の値がどの通貨だったのかを
-- 決められなくなるため。

ALTER TABLE album ADD COLUMN base_price_amount INTEGER;
ALTER TABLE album ADD COLUMN base_price_currency VARCHAR(3);

-- 額が負にならないことを保証する（V8 の chk_album_distribution_* と同じ意図）
ALTER TABLE album ADD CONSTRAINT chk_album_base_price_amount
    CHECK (base_price_amount IS NULL OR base_price_amount >= 0);

-- 通貨だけが入っている行を作れないようにする（額の無い通貨は意味を持たない）
ALTER TABLE album ADD CONSTRAINT chk_album_base_price_currency_requires_amount
    CHECK (base_price_currency IS NULL OR base_price_amount IS NOT NULL);

COMMENT ON COLUMN album.base_price_amount IS '頒布の基準額（通貨の最小単位。NULL は額が決まっていない）';
COMMENT ON COLUMN album.base_price_currency IS '頒布の基準額の通貨コード（ISO 4217。NULL は円）';
