import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
    state: () => ({
        sidebar: {
            opened: true,
        },
        theme: 'dark', // 'dark' | 'light'
        device: 'desktop', // 'desktop' | 'mobile'
    }),

    actions: {
        toggleSidebar() {
            this.sidebar.opened = !this.sidebar.opened
        },

        closeSidebar() {
            this.sidebar.opened = false
        },

        openSidebar() {
            this.sidebar.opened = true
        },

        toggleTheme() {
            this.theme = this.theme === 'dark' ? 'light' : 'dark'
            this.applyTheme()
        },

        setTheme(theme) {
            this.theme = theme
            this.applyTheme()
        },

        applyTheme() {
            const html = document.documentElement
            if (this.theme === 'dark') {
                html.classList.add('dark')
            } else {
                html.classList.remove('dark')
            }
            localStorage.setItem('pm-theme', this.theme)
        },

        initTheme() {
            const saved = localStorage.getItem('pm-theme')
            if (saved) {
                this.theme = saved
            }
            this.applyTheme()
        },

        setDevice(device) {
            this.device = device
        },
    },
})
