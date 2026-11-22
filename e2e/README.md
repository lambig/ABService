# 実スタックのE2E

Node・Java・依存の準備は[ルートREADME](../README.md)、PostgreSQL・MinIOの構成とデータ保存先は[Dockerガイド](../docker/README.md)を参照する。

リポジトリのルートで次を実行する。バックエンド用のターミナルはテストが終わるまで開いておく。

```sh
docker compose up -d --build postgres minio minio-init
npm run start:backend:e2e
```

別のターミナルから `npm run test:e2e` を実行する。バックエンドを変更した場合は停止して起動し直す。起動コマンドはJARを組み上げ、CIと共通の[start-backend.mjs](scripts/start-backend.mjs)で実行する。hot reloadは使わない。手動開発は `npm run dev:backend` を使う。

この起動処理はdevプロファイル、ポート8090、専用DB `abservice_e2e` を使用し、起動ごとにそのDBを初期化する。開発用・統合テスト用DBは対象にしない。異なる `DB_NAME` が設定されている場合は起動を拒否する。接続先はローカル検証環境に限り、実環境の接続設定を渡さない。DB接続やS3の設定項目は[application.properties](../backend/src/main/resources/application.properties)を正とする。

`prepare-stack.mjs` はバックエンドの準備完了を待ち、シードを投入してフロントを組み上げる。起動・停止は担当しない。起動失敗はバックエンドのログ、シード失敗はpreparationのシード段階、画面テスト失敗はPlaywrightの結果で判別する。CIでは `backend.log` と各ステップの結果を確認する。

ローカルでは起動したターミナルでCtrl+Cを押してバックエンドを停止する。CIでは停止ステップが起動プロセスへ終了を伝え、子のJavaプロセスを停止する。PostgreSQL・MinIOの停止とデータ管理はDockerガイドを参照する。

このアプリのDB・シード・ポートはABServiceのE2E用であり、汎用Skeletonの初期値にはしない。固定成果物の起動、シード、テストを分離する手順だけを再利用判断の対象とする。
