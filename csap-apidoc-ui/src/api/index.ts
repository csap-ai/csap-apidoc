import NProgress from '@/config/nprogress';
import axios, {
  AxiosInstance,
  AxiosError,
  AxiosRequestConfig,
  AxiosResponse,
} from 'csap-axios';
import {
  showFullScreenLoading,
  tryHideFullScreenLoading,
} from '@/config/serviceLoading';
import { ResultEnum } from '@/enums/httpEnum';
import { checkStatus } from './helper/checkStatus';
import { AxiosCanceler } from './helper/axiosCancel';
import { message } from 'antd';

const axiosCanceler = new AxiosCanceler();

// * 请求响应参数(不包含data)
export interface Result {
  code: string;
  message: string;
}

// * 请求响应参数(包含data)
export interface ResultData<T = any> extends Result {
  data: T;
}

const config = {
  // 默认地址请求地址，可在 .env 开头文件中修改
  // baseURL: import.meta.env.VITE_API_URL as string,
  // 设置超时时间（10s）
  timeout: 10000,
  // 跨域时候允许携带凭证
  withCredentials: true,
};

class RequestHttp {
  service: AxiosInstance;
  public constructor(config: AxiosRequestConfig) {
    // 实例化axios
    this.service = axios.create(config);

    /**
     * @description 请求拦截器
     * 客户端发送请求 -> [请求拦截器] -> 服务器
     */
    this.service.interceptors.request.use(
      (config: AxiosRequestConfig) => {
        return config; // 完全跳过所有请求拦截逻辑
      },
      (error: AxiosError) => {
        return Promise.reject(error);
      }
    );

    /**
     * @description 响应拦截器
     *  服务器换返回信息 -> [拦截统一处理] -> 客户端JS获取到信息
     */
    this.service.interceptors.response.use(
      (response: AxiosResponse) => {
        // 处理JSON格式异常的响应
        if (typeof response.data === 'string') {
          try {
            // 尝试修复缺少逗号的JSON格式
            const fixedJson = response.data
              .replace(/"([^"]+)"([^{}\\[\\]]*)([^{}\\[\\]]+)"([^"]+)"/g, '"$1"$2,$3"$4"')
              .replace(/"([^"]+)"([^{}\\[\\]]*)([^{}\\[\\]]+)"([^"]+)"/g, '"$1"$2,$3"$4"')
              .replace(/([}\\]])([^{}\\[\\]]+)([{\\[])/g, '$1,$2$3')
              .replace(/([}\\]])([^{}\\[\\]]+)([{\\[])/g, '$1,$2$3');
            
            response.data = JSON.parse(fixedJson);
          } catch (e) {
            console.error('JSON格式修复失败:', e);
            // 如果修复失败，返回原始数据
          }
        }
        return response;
      },
      async (error: AxiosError) => {
        const { response } = error;
        NProgress.done();
        tryHideFullScreenLoading();
        if (error.message.indexOf('timeout') !== -1)
          message.error('请求超时，请稍后再试');
        // 根据响应的错误状态码，做不同的处理
        if (response) checkStatus(response.status);
        return Promise.reject(error);
      }
    );
  }

  // * 常用请求方法封装
  get<T>(url: string, params?: object, _object = {}): Promise<ResultData<T>> {
    return this.service.get(url, { params, ..._object });
  }
  post<T>(url: string, params?: object, _object = {}): Promise<ResultData<T>> {
    return this.service.post(url, params, _object);
  }
  put<T>(url: string, params?: object, _object = {}): Promise<ResultData<T>> {
    return this.service.put(url, params, _object);
  }
  delete<T>(url: string, params?: any, _object = {}): Promise<ResultData<T>> {
    return this.service.delete(url, { params, ..._object });
  }
}

export default new RequestHttp(config);
