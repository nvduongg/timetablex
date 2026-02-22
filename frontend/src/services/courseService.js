import axios from '../utils/axiosConfig';

export const getCourses = () => axios.get('/courses');
export const createCourse = (data) => axios.post('/courses', data);
export const updateCourse = (id, data) => axios.put(`/courses/${id}`, data);
export const deleteCourse = (id) => axios.delete(`/courses/${id}`);
export const downloadTemplate = () => axios.get('/courses/template', { responseType: 'blob' });
export const importCourse = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post('/courses/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
};