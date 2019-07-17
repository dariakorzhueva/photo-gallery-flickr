package android.bignerdranch.com.photogallery;

import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "PhotoGalleryFragment";

    private List<GalleryItem> mItems = new ArrayList<>();
    private GridLayoutManager mGridLayoutManager;
    private FetchItemsTask mFetchTask;
    private RecyclerView mPhotoRecyclerView;
    private TextView mCurrentPageView;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    private int mCurrentPage = 1;
    private int mItemsPerPage;
    private int mMaxPage;
    private int mMaxItems;
    private boolean mLoading = false;


    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Удержание фрагмента */
        setRetainInstance(true);

        mFetchTask = new FetchItemsTask();
        mFetchTask.execute(mCurrentPage);

        mThumbnailDownloader = new ThumbnailDownloader<>();
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mCurrentPageView = (TextView) v.findViewById(R.id.current_page_view);
        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.photo_recycler_view);
        mGridLayoutManager = new GridLayoutManager(getActivity(), 3);
        mPhotoRecyclerView.setLayoutManager(mGridLayoutManager);

        /* Пролистывание элементов в RecyclerView */
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 || dy < 0) {
                    if (!(mLoading) &&
                            (dy > 0) &&
                            (mCurrentPage < mMaxPage) &&
                            mGridLayoutManager.findLastVisibleItemPosition() >= (mItems.size() - 1)) {

                        Log.d(TAG, "Fetching more items");
                        mLoading = true;
                        mCurrentPage++;

                        mFetchTask.cancel(true);
                        mFetchTask = new FetchItemsTask();
                        mFetchTask.execute(mCurrentPage);// Also updates current page view
                    } else {
                        int firstVisibleItem = mGridLayoutManager.findFirstVisibleItemPosition();
                        int calcPage = 0;

                        /* Если первый видимый элемент меньше количества элементов на странице
                        * То это первая страница
                        * Иначе - вычислить страницу */
                        if (firstVisibleItem < mItemsPerPage) {
                            calcPage = 1;
                        } else {
                            calcPage = (firstVisibleItem / mItemsPerPage) +
                                    (firstVisibleItem % mItemsPerPage == 0 ? 0 : 1);
                        }

                        if (calcPage != mCurrentPage) {
                            mCurrentPage = calcPage;
                        }

                        setCurrentPageView(firstVisibleItem);
                    }
                }
            }
        });
        setupAdapter();

        return v;
    }

    @Override
    public void onStop() {
        super.onStop();
        mFetchTask.cancel(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mFetchTask.cancel(true);
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    private void setCurrentPageView(int firstVisibleItem) {
        if (firstVisibleItem == -1) {
            firstVisibleItem = mGridLayoutManager.findFirstVisibleItemPosition();
        }
        mCurrentPageView.setText("Current Fetched Page: " + mCurrentPage +
                " of " + ((mMaxPage == 0) ? "<unknown>" : mMaxPage) +
                ", " + ((mItemsPerPage == 0) ? "<unknown>" : mItemsPerPage) + " items per page" +
                ", " + ((mMaxItems == 0) ? "<unknown>" : mMaxItems) + " total items" +
                ", you've scrolled past: " + (firstVisibleItem <= 0 ? 0 : firstVisibleItem) +
                " items.");
    }

    private void setupAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);

            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {

        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, viewGroup, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder photoHolder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            Drawable placeholder = getResources().getDrawable(R.drawable.bill_up_close);
            photoHolder.bindDrawable(placeholder);

            mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Integer, Void, List<GalleryItem>> {

        private static final int COLUMNS_SIZE = 200;
        private int mGridColumns;

        @Override
        protected List<GalleryItem> doInBackground(Integer... params) {
            FlickrFetchr mFetcher = new FlickrFetchr();
            mFetcher.fetchItems(params[0]);
            mMaxPage = mFetcher.getMaxPages();
            mItemsPerPage = mFetcher.getItemsPerPage();
            mMaxItems = mFetcher.getTotalItems();

            return mFetcher.fetchItems(params[0]);
        }

        /* Обновление интерфейса после загрузки фотографий */
        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            if (mItems.size() == 0) {
                mItems.addAll(items);
                setupAdapter();
                setCurrentPageView(-1);
            } else {
                final int oldSize = mItems.size();
                mItems.addAll(items);

                mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        mPhotoRecyclerView.smoothScrollToPosition(oldSize);

                        /* Определение ширины столбцов на основе ширины RecyclerView */
                        int width = mPhotoRecyclerView.getWidth();
                        mGridColumns = width / COLUMNS_SIZE;
                        mGridLayoutManager = new GridLayoutManager(getActivity(), mGridColumns);
                        mPhotoRecyclerView.setLayoutManager(mGridLayoutManager);
                        setCurrentPageView(-1);

                        mLoading = false;
                    }
                });
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
            }
        }
    }
}
