export function distributeProportionally(total: number, weights: number[]): number[] {
  if (weights.length === 0) return []
  const weightSum = weights.reduce((s, w) => s + w, 0)
  if (weightSum === 0) return weights.map(() => 0)
  let remaining = total
  return weights.map((w, i) => {
    if (i === weights.length - 1) {
      return Math.round(remaining * 100) / 100
    }
    const value = Math.round(total * (w / weightSum) * 100) / 100
    remaining -= value
    return value
  })
}
