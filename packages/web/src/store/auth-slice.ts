import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface AuthState {
  token: string | null;
  refreshToken: string | null;
  email: string | null;
  role: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

const initialState: AuthState = {
  token: null,
  refreshToken: null,
  email: null,
  role: null,
  isAuthenticated: false,
  isLoading: true,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials(state, action: PayloadAction<{ token: string; refreshToken: string; email: string; role: string }>) {
      state.token = action.payload.token;
      state.refreshToken = action.payload.refreshToken;
      state.email = action.payload.email;
      state.role = action.payload.role;
      state.isAuthenticated = true;
      state.isLoading = false;
    },
    logout(state) {
      state.token = null;
      state.refreshToken = null;
      state.email = null;
      state.role = null;
      state.isAuthenticated = false;
      state.isLoading = false;
    },
    setLoading(state, action: PayloadAction<boolean>) {
      state.isLoading = action.payload;
    },
  },
});

export const { setCredentials, logout, setLoading } = authSlice.actions;
export default authSlice.reducer;
