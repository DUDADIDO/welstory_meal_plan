export default function PrimaryButton({ children, className = '', ...props }) {
  return (
    <button
      type="button"
      className={`rounded-full bg-apple-blue px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-[#0077ed] disabled:opacity-45 ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
