package com.abservice.application.port;

import io.smallrye.mutiny.Uni;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.Callable;

/** 有界の検査・保存と、保存済み実体の照合による確定復旧。入力は受入後にworkerで開く。 */
public interface PrivateAudioOperations {
    Uni<FlacMetadata> ingest(UUID id, Callable<InputStream> source);
    Uni<FlacMetadata> recover(UUID id);
}
