export function isSpecialMealDay(meals) {
  if (!Array.isArray(meals)) return false

  const regularMealCount = meals.filter(({ courseName }) => courseName?.trim() !== '추가배식대').length

  return regularMealCount > 0 && regularMealCount <= 3
}
