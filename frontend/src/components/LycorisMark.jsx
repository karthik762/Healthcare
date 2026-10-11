export function LycorisMark({ size = 32, className = "" }) {
    return (
        <svg
            width={size}
            height={size}
            viewBox="0 0 40 40"
            aria-hidden="true"
            className={`lycoris-mark-svg ${className}`}
            style={{ flexShrink: 0 }}
        >
            {[0, 60, 120, 180, 240, 300].map((a) => (
                <path
                    key={a}
                    d="M20 20 C 22 12, 30 8, 33 12 C 35 15, 31 17, 29 14"
                    fill="none"
                    stroke="#E3131B"
                    strokeWidth="2.4"
                    strokeLinecap="round"
                    transform={`rotate(${a} 20 20)`}
                />
            ))}
            <circle cx="20" cy="20" r="2.5" fill="#F2EBDD" />
        </svg>
    );
}

export default LycorisMark;
