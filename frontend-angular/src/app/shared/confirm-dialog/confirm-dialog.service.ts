import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';

export interface ConfirmOptions {
  title?: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'default' | 'danger';
}

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private visibleSubject = new Subject<boolean>();
  private optionsSubject = new Subject<ConfirmOptions | null>();
  private resultSubject: Subject<boolean> | null = null;

  visible$ = this.visibleSubject.asObservable();
  options$ = this.optionsSubject.asObservable();

  confirm(opts: ConfirmOptions | string): Observable<boolean> {
    const options: ConfirmOptions = typeof opts === 'string' ? { message: opts } : opts;
    this.optionsSubject.next(options);
    this.visibleSubject.next(true);
    this.resultSubject = new Subject<boolean>();
    return this.resultSubject.asObservable();
  }

  accept(): void {
    this.visibleSubject.next(false);
    this.resultSubject?.next(true);
    this.resultSubject?.complete();
    this.resultSubject = null;
  }

  cancel(): void {
    this.visibleSubject.next(false);
    this.resultSubject?.next(false);
    this.resultSubject?.complete();
    this.resultSubject = null;
  }
}
