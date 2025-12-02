package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.vo.ValueObject;

/**
 * 再生時間の値オブジェクト
 *
 * <p>
 * トラックの再生時間を表す値オブジェクトです。 ミリ秒単位で保持します。
 * </p>
 * <ul>
 * <li>nullは許可されません</li>
 * <li>負の値は許可されません</li>
 * </ul>
 *
 * @param milliseconds
 *            再生時間（ミリ秒）
 */
public record Duration(Integer milliseconds) implements ValueObject<Duration> {
    /**
     * コンストラクタ
     *
     * @param milliseconds
     *            再生時間（ミリ秒）
     * @throws IllegalArgumentException
     *             ミリ秒がnullまたは負の値の場合
     */
    public Duration {
        if (milliseconds == null) {
            throw new IllegalArgumentException("Duration milliseconds cannot be null");
        }
        if (milliseconds < 0) {
            throw new IllegalArgumentException("Duration milliseconds cannot be negative");
        }
    }

    /**
     * 秒単位で時間を取得
     *
     * @return 秒数
     */
    public int toSeconds() {
        return milliseconds / 1000;
    }

    /**
     * MM:SS形式で時間を取得
     *
     * @return MM:SS形式の文字列
     */
    public String toMinutesSeconds() {
        var totalSeconds = toSeconds();
        var minutes = totalSeconds / 60;
        var seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * HH:MM:SS形式で時間を取得
     *
     * @return HH:MM:SS形式の文字列
     */
    public String toHoursMinutesSeconds() {
        var totalSeconds = toSeconds();
        var hours = totalSeconds / 3600;
        var minutes = (totalSeconds % 3600) / 60;
        var seconds = totalSeconds % 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public boolean equivalentTo(Duration other) {
        if (other == null) {
            return false;
        }
        return this.milliseconds.equals(other.milliseconds);
    }
}
