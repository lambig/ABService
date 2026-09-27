package com.abservice.application.port;

import java.io.IOException;
import java.io.InputStream;

/**
 * FLAC全体を検証し、検証した実体そのものを保持する境界。 ブロッキングI/Oを伴うため、呼び出し側はイベントループの外で実行する。
 * 入力の所有権・読込タイムアウトは呼び出し側が持つ。成功時の実体は必ずcloseする。
 */
public interface FlacInspector {
    InspectedAudio inspect(InputStream source, FlacInspectionLimits limits) throws IOException;
}
