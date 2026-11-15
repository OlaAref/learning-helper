import type { ReactNode } from 'react'

const Svg = ({ children, size = 16, stroke = 2 }: { children: ReactNode; size?: number; stroke?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={stroke}
       strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{children}</svg>
)

export const PageIcon = () => <Svg stroke={1.8}><path d="M7 3h8l4 4v14H7z" /><path d="M15 3v4h4" /></Svg>
export const PlusIcon = () => <Svg><path d="M12 5v14M5 12h14" /></Svg>
export const SearchIcon = () => <Svg><circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" /></Svg>
export const SyncIcon = () => <Svg><path d="M20 11a8 8 0 10-2.3 5.7" /><path d="M20 4v7h-7" /></Svg>
export const ChevronIcon = () => <Svg size={14}><path d="M9 6l6 6-6 6" /></Svg>
export const FolderIcon = () => <Svg><path d="M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" /></Svg>
export const MoonIcon = () => <Svg><path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z" /></Svg>
export const SunIcon = () => (
  <Svg><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></Svg>
)
export const BackIcon = () => <Svg stroke={2.2}><path d="M19 12H5M11 6l-6 6 6 6" /></Svg>
export const ArrowIcon = () => <Svg stroke={2.2}><path d="M5 12h14M13 6l6 6-6 6" /></Svg>
export const GridIcon = () => (
  <Svg size={18}><rect x="4" y="4" width="7" height="7" rx="1.5" /><rect x="13" y="4" width="7" height="7" rx="1.5" /><rect x="4" y="13" width="7" height="7" rx="1.5" /><rect x="13" y="13" width="7" height="7" rx="1.5" /></Svg>
)
export const DatabaseIcon = () => (
  <Svg size={24} stroke={1.8}><ellipse cx="12" cy="5.5" rx="7" ry="2.8" /><path d="M5 5.5v13c0 1.5 3.1 2.8 7 2.8s7-1.3 7-2.8v-13" /><path d="M5 12c0 1.5 3.1 2.8 7 2.8s7-1.3 7-2.8" /></Svg>
)
export const BigSyncIcon = () => <Svg size={24} stroke={1.8}><path d="M20 11a8 8 0 10-2.3 5.7" /><path d="M20 4v7h-7" /></Svg>
