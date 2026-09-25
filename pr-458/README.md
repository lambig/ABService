# PR #458 browser evidence

Code: `2018aadcbe258a7fe833dde16aba6c71873b85a9`. Synthetic fixtures only; no production content, credentials, deployment or account values.

`article.png`: description → tracks (performer, two tunes and composer/arranger) → source note → notice/store → event/price. The album description is not duplicated in the article.

`album-heading-first.png`: standalone album whose description starts with a heading; tracks precede that notice.

`fixture.json` contains the inputs. For the second case replace album.description with `## 注意事項\n\n説明が見出しから始まる場合も、注意事項は曲目の下です。`. Render with public-presentation's renderArticle/renderAlbum, renderPageFrame/renderSiteNav and the admin article CSS from the recorded code. HTML is locally generated; its hashes, the source file hashes, CSS/bundle hashes, image hashes and accessibility transcripts are recorded in provenance.json. The screenshots are actual browser captures, not image edits.

Scope: offline shared renderer. This is not an admin interaction test or deployed acceptance. No audio is configured in these fixtures. Backend/E2E CI remains blocked before tests by the MinIO registry authentication error; see PR #458 and issue #459. These images do not replace that E2E acceptance.
