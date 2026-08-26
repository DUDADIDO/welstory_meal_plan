export default function LoadingGrid() {
  return (
    <section aria-label="식단을 불러오는 중" aria-busy="true" className="mx-auto grid max-w-7xl gap-5 px-5 pb-24 sm:grid-cols-2 sm:px-8 lg:grid-cols-3">
      {Array.from({ length: 6 }, (_, item) => <div className="aspect-[4/3] animate-pulse rounded-[1.75rem] bg-black/5" key={item} />)}
    </section>
  )
}
