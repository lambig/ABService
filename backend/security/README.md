# TLS信頼ストアの補完

eMudhra中間CAの問題は、OS・Node.js・JDKが別々の信頼ストアを使うため、それぞれで対処が必要になる。Node.jsは公式セキュリティ版を使い、OSはCAパッケージを更新する。Amazon Linuxの修正版が未提供の間は、Ubuntuと同じ二つのルートをp11-kitの拒否リストに置く。これらのルートにのみ連鎖する正当な接続も拒否されるため、接続先側の証明書更新が必要になる場合がある。

JDKではルート全体の削除に代えて、OpenJDKの中間証明書ブロックを既存の`blocked.certs`へ追記する。既存のベンダー設定と利用者の追記を保持する。OSのCA更新だけでJDK独自のストアが修正されたとは判定しない。

`emudhra-blocked.certs`は[OpenJDKの修正22bc7357](https://github.com/openjdk/jdk/commit/22bc7357ce6bc5f1ed2f1e83e0544b2c25f781d4)の公開証明書から、同リポジトリの`BlockedCertsConverter`方式で生成したSHA-256指紋。署名の等価表現を含む。ルート証明書も同コミットの`cacerts/emsigneccrootcag3`と`cacerts/comodoaaaca`を出典とする。

根拠: [OracleのJDK対策](https://ops.java/emudhra)、[UbuntuのCA対策](https://ubuntu.com/blog/emudhra-ca-compromise-fixes-available)、[Amazon Linuxの修正状況](https://explore.alas.aws.amazon.com/CVE-2026-113470.html)。ベンダーの修正版採用後も、実際の拒否を検証してから補完を撤去する。
