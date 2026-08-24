# ABService Documentation

## ドキュメント記述規約

`docs/` 配下の全ドキュメント（および設計・仕様を記述する Markdown 全般）に適用する共通ルール:

- **実装が正になった事実を文書に再記述しない**。テーブル定義・カラム一覧・型階層・クラス構成・APIの形・コード例は、マイグレーション（`backend/src/main/resources/db/migration/`）・実クラス・静的解析の強制ルールが正。文書に置くと二重管理になり、必ず乖離する。
- **文書に残すのは実装から導けないものだけ**: なぜその構造にしたかという判断（[DECISIONS.md](DECISIONS.md)）、どう書くかの意図（[CODING_GUIDELINES.md](CODING_GUIDELINES.md)）、まだ実装がない計画（[STATUS_AND_ROADMAP.md](STATUS_AND_ROADMAP.md)・GitHub issue）。
- **実装が入った時点で、その事実を記述していた文書・節は削除する**。「設計→実装」の途中で書いた文書は、実装が入れば役目を終える。判断の記録だけを [DECISIONS.md](DECISIONS.md) へ移して本体は消す。
- **状態を現在形で記述する**。「いま何がどうなっているか」を書く。
- **作業ログ（実施イベント）を残さない**。`✅完了` / `〜を追加した` / `〜を実装した` / 「（フェーズX完了を反映）」といったイベント記録は書かない。**何をいつ実施したかの正は git コミット履歴**とする。
- **同じ事実を複数箇所に重複記載しない**。状態が変わったら1箇所だけ更新し、残タスク一覧の完了項目は「完了」マークではなく**削除**する。
- **フロー情報は GitHub issue、ストック文書は md**。残タスク・技術的負債・段階移行・進行状況・作業の経緯は issue に置き、md へ転記しない。[DECISIONS.md](DECISIONS.md)（設計判断の現在の正）・[ARCHITECTURE.md](ARCHITECTURE.md)・[CODING_GUIDELINES.md](CODING_GUIDELINES.md)・[../backend/TEST_GUIDE.md](../backend/TEST_GUIDE.md) はストック文書であり、issue へ寄せる対象ではない。

## ドキュメント一覧

| ドキュメント | 役割 |
|---|---|
| [STATUS_AND_ROADMAP.md](STATUS_AND_ROADMAP.md) | 開発状況と残タスク。記載事項ゼロへ収束させる（本番リリースに向けた整備タスクは GitHub issue #132 が正） |
| [DECISIONS.md](DECISIONS.md) | 設計判断の記録。なぜその構造にしたか（実装から導けない部分だけ） |
| [ARCHITECTURE.md](ARCHITECTURE.md) | システム構成・認証方式・アセット経路など、コード単体からは見えない全体像 |
| [CODING_GUIDELINES.md](CODING_GUIDELINES.md) | 設計上の意図と、静的解析で強制しているルールの索引 |
| [../backend/TEST_GUIDE.md](../backend/TEST_GUIDE.md) | ユニット／統合テストの分離規約と実行方法 |

## 開発環境・コマンド

セットアップ手順・ビルド／テストコマンドは [../README.md](../README.md)、開発ルール（実装承認プロセス・コミット規約・PR要件）は [../CONTRIBUTION.md](../CONTRIBUTION.md)、ローカルインフラ（PostgreSQL・MinIO）は [../docker/README.md](../docker/README.md)、AWS構成とデプロイは [../infra/README.md](../infra/README.md) が正。
