import http from '@/api';
import { ResultData } from './index';

interface Result<T> {
  data: T;
  message: string;
  code: string;
}

interface DataList {
  [x: string]: any;
  apiList: any[];
}

interface IParams {
  name: string;
  key: string;
  [name: string]: any;
}

// * 获取树
export const getMenuList = (url: string) => {
  return http.get<DataList>(url);
};

// * 获取子节点详情
export const getApiDetail = (qxapi: string, params: IParams) => {
  return http.get<DataList>(qxapi, params, { headers: { noLoading: true } });
};

// * 当前数据发送请求
export const requesGetMethods = (qxapi: string, params?: any) => {
  return http.get(qxapi, { params });
};

export const requestPostMethods = (qxapi: string, data?: any) => {
  return http.post(qxapi, data);
};
