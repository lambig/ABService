package com.abservice.domain.repository.album;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.repository.Repository;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * アルバムリポジトリ
 *
 * <p>
 * Album集約の永続化と、書き込みを目的とした取得を担当します。条件で絞る照会は Read Model 側
 * （{@code AlbumDataSource}）の役目のため、書き込み側の finder は持ちません（取得の口を絞ることで、
 * 主張を伴わない取得を残さない）。
 * </p>
 */
public interface AlbumRepository extends Repository<Album, Album.Id> {

    /**
     * IDでアルバムを取得する（主張を伴わない取得）
     *
     * <p>
     * 業務コードはこの取得を使わない。アルバムを取得する側は、編集権か参照のいずれかを主張したうえで {@code AlbumAccessService}
     * を通す（ArchUnitが検査する）。基底インターフェースからの継承のままでは呼び出しが
     * どの集約のリポジトリに向いたものか静的に追えないため、ここで再宣言して検査可能にしている。
     * </p>
     *
     * @param id
     *            アルバムID
     * @return アルバム、存在しない場合はnull
     */
    @Override
    Uni<Album> findById(Album.Id id);

    /**
     * IDでアルバムを取得し、呼び出し元のトランザクションが終わるまで他のトランザクションの更新を待たせる
     *
     * <p>
     * 集約をまたぐ不変条件は、判定に使ったアルバムが判定から書き込みまでの間に動かないことを前提にする。この取得は
     * その前提を満たすもので、取得したアルバムは呼び出し元のコミットまで他のトランザクションから更新されない。 業務コードからは
     * {@code AlbumAccessService} を通して使う。
     * </p>
     *
     * @param id
     *            アルバムID
     * @return アルバム、存在しない場合はnull
     */
    Uni<Album> findByIdExclusively(Album.Id id);

    /**
     * {@link #findByIdExclusively} と同じ取得を、その行の世代を伴って返す
     *
     * <p>
     * 世代（{@link Revision}）はドメインの語彙ではなく、**更新の契約**である。編集を始めた時点の世代を要求が
     * 持ち込み、保存の直前に読んだ世代と突き合わせることで、途中で別の操作が保存したかどうかを判定する（#287）。
     * 業務モデルへ持たせないのは、作品の事実ではないものを集約の状態に混ぜないためである。
     * </p>
     *
     * @param id
     *            アルバムID
     * @return アルバムと行の世代。存在しない場合はnull
     */
    Uni<Revisioned> findByIdExclusivelyWithRevision(Album.Id id);

    /**
     * 保存し、保存後の行の世代を伴って返す
     *
     * <p>
     * 保存で世代は進む。呼び出し元が次の更新の条件として返せるようにするため、進んだ後の値を伴わせる
     * （進み方を呼び出し元が推測すると、実装の都合が契約になる）。
     * </p>
     *
     * @param aggregate
     *            保存するアルバム
     * @return 保存後のアルバムと行の世代
     */
    Uni<Revisioned> saveWithRevision(Album aggregate);

    /**
     * 行の世代。更新の条件として運ぶだけの値で、業務上の意味を持たない
     *
     * @param value
     *            世代（保存のたびに進む）
     */
    record Revision(int value) {
    }

    /**
     * アルバムと、その行の世代の組
     *
     * @param album
     *            アルバム
     * @param revision
     *            行の世代
     */
    record Revisioned(Album album, Revision revision) {
    }

    /**
     * 複数のIDでアルバムを取得する（主張を伴わない取得）
     *
     * <p>
     * {@link #findById} と同じ理由で業務コードからは使わず、ここで再宣言して検査可能にしている。
     * </p>
     *
     * @param ids
     *            アルバムIDのIterable
     * @return 取得したアルバムのリスト
     */
    @Override
    Uni<List<Album>> findAllById(Iterable<Album.Id> ids);

    /**
     * すべてのアルバムを取得する（主張を伴わない取得）
     *
     * <p>
     * {@link #findById} と同じ理由で業務コードからは使わず、ここで再宣言して検査可能にしている。
     * </p>
     *
     * @return すべてのアルバムのリスト
     */
    @Override
    Uni<List<Album>> findAll();

    /**
     * 当該チューンを参照しているトラック内チューン構成があるか確認する
     *
     * <p>
     * チューンはアルバムから独立して存在するが、参照はアルバム集約の内側（{@code Track} の
     * {@code TrackTune}）にある。参照している側を数える問いのため、アルバム側のリポジトリが担う。
     * </p>
     *
     * @param tuneId
     *            チューンID
     * @return 参照している構成が1件以上あればtrue
     */
    Uni<Boolean> existsTrackTuneReferencing(Tune.Id tuneId);
}
