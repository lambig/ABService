/* eslint-disable functional/no-let, functional/immutable-data -- FFT scratch buffers are private to a synchronous instance; input and returned values are never retained or mutated. */

/** 片側スペクトルの二乗振幅。検証済みの2冪長専用。DC/Nyquist補正は呼び出し元が担う。 */
export const createSquaredSpectrum = (size: number) => {
  const real = new Float64Array(size);
  const imaginary = new Float64Array(size);
  return (samples: ArrayLike<number>): readonly number[] => {
    // INVARIANT: Previous imaginary values must not leak into the next real-input transform.
    imaginary.fill(0);
    // PERF: Avoid recursive even/odd arrays and complex objects on the audio rendering thread.
    for (let index = 0, reversed = 0; index < size; index += 1) {
      // INVARIANT: index is below the validated input length.
      real[reversed] = samples[index] as number;
      let bit = size >> 1;
      while (bit > 0 && (reversed & bit) !== 0) {
        reversed ^= bit;
        bit >>= 1;
      }
      reversed ^= bit;
    }
    for (let width = 2; width <= size; width *= 2) {
      const half = width / 2;
      for (let offset = 0; offset < half; offset += 1) {
        const angle = (-2 * Math.PI * offset) / width;
        const cosine = Math.cos(angle);
        const sine = Math.sin(angle);
        for (let start = offset; start < size; start += width) {
          const other = start + half;
          // INVARIANT: power-of-two stages keep both butterfly indices inside the buffers.
          const evenReal = real[start] as number;
          const evenImaginary = imaginary[start] as number;
          const oddReal = real[other] as number;
          const oddImaginary = imaginary[other] as number;
          const rotatedReal = oddReal * cosine - oddImaginary * sine;
          const rotatedImaginary = oddReal * sine + oddImaginary * cosine;
          real[start] = evenReal + rotatedReal;
          imaginary[start] = evenImaginary + rotatedImaginary;
          real[other] = evenReal - rotatedReal;
          imaginary[other] = evenImaginary - rotatedImaginary;
        }
      }
    }
    return Array.from({ length: size / 2 + 1 }, (_, index) => {
      // INVARIANT: the one-sided spectrum includes indices 0 through size/2.
      const r = real[index] as number;
      const i = imaginary[index] as number;
      return r * r + i * i;
    });
  };
};

/** 単発呼び出し用。返した配列は後続の解析でも変更されない。 */
export const squaredSpectrum = (
  samples: readonly number[],
): readonly number[] => createSquaredSpectrum(samples.length)(samples);
