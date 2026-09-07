# 入力エラー契約の先行スライス (#288)

`Result.mapErrorFields` は成功値には触れず、エラーの順序・message/codeを保持してfieldのみ変換する。
VOは再利用可能なローカルfieldを返し、application inputを組み立てる場所でAPI入力パスへ対応付ける。
`field -> "tracks[0]." + field` のように入れ子・配列添字へも合成できる。全体エラーを表す空のfieldを扱う場合は
mapper側で空を維持し、特定項目のエラーへ偽装しない。

今回の適用はUpdateAlbumService。title / artistDisplayName / catalogNumber / isdn / coverImageKey /
description / descriptionFormat / event.nameを対応付け、既存のreleaseDate / event.dateは維持する。
作成・初期トラック登録・他の更新サービスへの横展開、複数trackの全エラー収集は後続。

管理API clientはhttp.ts経由に統一し、HTTP statusとProblem Details（errors.fieldを含む）を保持する。
UIの既存ok / unauthorized / failedの枝は維持し、reasonで通信失敗・HTTP失敗・成功応答の読み取り失敗を区別する。
JSON必須の操作で204を任意のTとして成功扱いせず、空本文操作にはrequestEmpty（ApiResult<void>）を使う。
現在の操作はすべて本文を要求するためrequestJsonを利用する。

バックエンドから返ったfieldを使う設計であり、フロントへcode→field対応表を置かない。
フォーム項目への表示E2Eは #122 の編集画面実装時に残す。実APIの400応答による入力パス検証も後続。
ユーザーの「順に実行してください」に基づく実装・PR発行。
