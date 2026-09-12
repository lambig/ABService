export const manifest = {
  schemaVersion: 1,
  packageVersion: "player-study-01",
  compatibleAppVersion: {
    minInclusive: [1, 1, 0],
    maxExclusive: [2, 0, 0],
  },
  albums: [
    {
      albumId: "study-01",
      title: "Northbound · Study 01",
      tracks: [
        {
          trackId: "first",
          title: "Reel study",
          audioAssetId: "tone-first",
        },
        {
          trackId: "second",
          title: "Air study",
          audioAssetId: "tone-second",
        },
      ],
    },
    {
      albumId: "study-02",
      title: "Evening Session",
      tracks: [
        {
          trackId: "return",
          title: "Return",
          audioAssetId: "tone-first",
        },
      ],
    },
  ],
  presentationAssetIds: [],
  assets: [
    {
      assetId: "tone-first",
      mediaType: "audio/flac",
      byteLength: 121308,
      checksum: {
        algorithm: "sha256",
        value:
          "661238d8df40b65f0cf1ede53004d8f8b5c6db3bcfd68b1aba813f47e360b9f9",
      },
      required: true,
    },
    {
      assetId: "tone-second",
      mediaType: "audio/flac",
      byteLength: 81415,
      checksum: {
        algorithm: "sha256",
        value:
          "db568e829a2f43e71613b16ad9a3b20348296a96bc7f704bb0bfe44354d5ef30",
      },
      required: true,
    },
  ],
};
