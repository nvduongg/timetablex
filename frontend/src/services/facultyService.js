import axios from '../utils/axiosConfig';

export const getFaculties = () => axios.get('/faculties');
export const createFaculty = (data) => axios.post('/faculties', data);
export const updateFaculty = (id, data) => axios.put(`/faculties/${id}`, data);
export const deleteFaculty = (id) => axios.delete(`/faculties/${id}`);
export const downloadTemplate = () => axios.get('/faculties/template', { responseType: 'blob' });
export const importFaculty = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post('/faculties/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
};