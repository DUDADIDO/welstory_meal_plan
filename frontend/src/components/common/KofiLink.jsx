import kofiCup from '../../assets/icons/kofi-cup.avif'

export default function KofiLink({ floating = false }) {
  return <a href="https://ko-fi.com/starfbsdud" target="_blank" rel="noopener noreferrer"
    aria-label={floating ? 'Ko-fi로 후원하기' : 'Support on Ko-fi'} title="Ko-fi로 응원하기 (새 탭)"
    className={`${floating ? 'grid size-13 place-items-center rounded-full shadow-lg hover:scale-105' : 'inline-flex items-center gap-2 whitespace-nowrap rounded-full px-3 py-2.5 text-xs font-semibold shadow-sm sm:px-4'} bg-[#ff6433] text-[#202020] transition hover:bg-[#ff784d] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#ff6433]`}>
    <img src={kofiCup} alt="" aria-hidden="true" className={floating ? 'size-8 object-contain' : 'size-5 object-contain'} />
    {!floating && <><span className="sm:hidden">Ko-fi</span><span className="hidden sm:inline">Support on Ko-fi</span></>}
  </a>
}
