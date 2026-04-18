import http from '@/api/asyncIndex';

interface Result {
  data: any;
  message: string;
  code: string;
}

interface DataList {
  apiList: any[];
}

interface IParams {
  name: string;
  key: string;
  [name: string]: any;
}
// * 当前数据发送请求 - GET方法
export const requesGetMethods = (qxapi: string, params?: any) => {
  return http.get<Result>(qxapi, params, {
    headers: { noLoading: true },
  });
};

// * 当前数据发送请求 - POST方法
export const requestPostMethods = (qxapi: string, data?: any) => {
  return http.post<DataList>(qxapi, data, {
    headers: { noLoading: true },
  });
};
