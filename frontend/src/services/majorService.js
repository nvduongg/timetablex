import axios from '../utils/axiosConfig';

export const getMajors = () => axios.get('/majors');
export const createMajor = (data) => axios.post('/majors', data);
export const updateMajor = (id, data) => axios.put(`/majors/${id}`, data);
export const deleteMajor = (id) => axios.delete(`/majors/${id}`);
export const downloadTemplate = () => axios.get('/majors/template', { responseType: 'blob' });
export const importMajor = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post('/majors/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
};