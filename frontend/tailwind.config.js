/** @type {import('tailwindcss').Config} */
export default {
    darkMode: 'class',
    content: [
        "./index.html",
        "./src/**/*.{vue,js,ts,jsx,tsx}",
    ],
    // Tailwind v4 reads theme tokens (incl. breakpoints) from CSS @theme blocks.
    // The custom `gridfeed` breakpoint (920px) lives in src/index.css.
    theme: {
        extend: {},
    },
    plugins: [require('tailwind-scrollbar-hide')],
}
