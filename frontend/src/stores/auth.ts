import { defineStore } from 'pinia';
import api from '../api/axios';
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { jwtDecode } from 'jwt-decode';

export const useAuthStore = defineStore('auth', () => {
    const token = ref<string | null>(localStorage.getItem('token'));
    const user = ref<any>(null);
    const router = useRouter();

    if (token.value) {
        try {
            user.value = jwtDecode(token.value);
        } catch (e) {
            token.value = null;
            localStorage.removeItem('token');
        }
    }

    const setToken = (newToken: string) => {
        token.value = newToken;
        localStorage.setItem('token', newToken);
        try {
            user.value = jwtDecode(newToken);
        } catch (e) {
            user.value = null;
        }
    };

    const logout = () => {
        token.value = null;
        user.value = null;
        localStorage.removeItem('token');
        router.push('/login');
    };

    const login = async (credentials: any) => {
        const response = await api.post('/auth/login', credentials);
        if (response.data.token) {
            setToken(response.data.token);
        }
    };

    const register = async (userData: any) => {
        // Returns { status: "PENDING_VERIFICATION", email } - no JWT yet
        const response = await api.post('/auth/register', userData);
        return response.data;
    };

    return { token, user, setToken, logout, login, register, isAuthenticated: () => !!token.value };
});
