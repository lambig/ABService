type Complex = Readonly<{ real: number; imaginary: number }>;

const combine = (
  even: readonly Complex[],
  odd: readonly Complex[],
): readonly Complex[] => {
  const pairs = even.map((value, index) => {
    // INVARIANT: 呼び出し元が2冪長を偶数・奇数へ分割するため、同じ添字の要素が必ず存在する。
    const other = odd[index] as Complex;
    const angle = (-Math.PI * index) / even.length;
    const real =
      other.real * Math.cos(angle) - other.imaginary * Math.sin(angle);
    const imaginary =
      other.real * Math.sin(angle) + other.imaginary * Math.cos(angle);
    return {
      plus: { real: value.real + real, imaginary: value.imaginary + imaginary },
      minus: {
        real: value.real - real,
        imaginary: value.imaginary - imaginary,
      },
    };
  });
  return [
    ...pairs.map((pair) => pair.plus),
    ...pairs.map((pair) => pair.minus),
  ];
};

/** 2冪長の内部入力専用。割り当てを許容して意味論を照合するradix-2参照FFT。 */
const transform = (values: readonly Complex[]): readonly Complex[] =>
  values.length === 1
    ? values
    : combine(
        transform(values.filter((_, index) => index % 2 === 0)),
        transform(values.filter((_, index) => index % 2 === 1)),
      );

/** 片側スペクトルの二乗振幅。DCとNyquistの係数補正は呼び出し元が担う。 */
export const squaredSpectrum = (
  samples: readonly number[],
): readonly number[] =>
  transform(samples.map((real) => ({ real, imaginary: 0 })))
    .slice(0, samples.length / 2 + 1)
    .map((value) => value.real ** 2 + value.imaginary ** 2);
