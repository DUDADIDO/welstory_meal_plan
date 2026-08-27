export function isSpecialMealDay(meals) {
  return Array.isArray(meals) && meals.length > 0 && meals.length <= 3
}
