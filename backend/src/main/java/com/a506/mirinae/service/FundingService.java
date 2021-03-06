package com.a506.mirinae.service;

import com.a506.mirinae.domain.category.Category;
import com.a506.mirinae.domain.category.CategoryRepository;
import com.a506.mirinae.domain.category.CategoryRes;
import com.a506.mirinae.domain.donation.Donation;
import com.a506.mirinae.domain.donation.DonationRepository;
import com.a506.mirinae.domain.donation.DonationReq;
import com.a506.mirinae.domain.funding.*;
import com.a506.mirinae.domain.user.User;
import com.a506.mirinae.domain.user.UserRepository;
import com.a506.mirinae.util.EthereumUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingService {
    private final UserRepository userRepository;
    private final FundingRepository fundingRepository;
    private final DonationRepository donationRepository;
    private final CategoryRepository categoryRepository;
    @Value("${blockchain.main.address}")
    private String address;
    
    @Value("${blockchain.main.owner}")
    private String owner;
    
    @Value("${blockchain.main.password}")
    private String password;
    
    @Value("${blockchain.main.contract}")
    private String contract;
    private EthereumUtil ethereumUtil = new EthereumUtil();
    @Transactional
    public FundingSizeRes getFundingList(String categoryId, Pageable pageable) {
        List<Funding> funding;
        List<FundingRes> fundingResList = new ArrayList<>();
        Long pageCount;
        if(categoryId.equals("all")) {
            funding = fundingRepository.findAllByFundingState(FundingState.ACCEPTED, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())).getContent();
            pageCount = (long) fundingRepository.findAllByFundingState(FundingState.ACCEPTED).size() / pageable.getPageSize() + 1;
        }
        else {
            funding = fundingRepository.findByCategory_IdAndFundingState(Long.parseLong(categoryId), FundingState.ACCEPTED ,PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())).getContent();
            pageCount = (long) fundingRepository.findByCategory_IdAndFundingState(Long.parseLong(categoryId), FundingState.ACCEPTED).size() / pageable.getPageSize() + 1;
        }
        for(Funding f : funding) {
            if(f.getDonations().size()==0)
                fundingResList.add(new FundingRes(f));
            else
                fundingResList.add(new FundingRes(donationRepository.findDonationByFundingId(f.getId())));
        }
        return new FundingSizeRes(fundingResList, pageCount);
    }

    public FundingIdRes createFunding(FundingReq fundingReq, Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("?????? User??? ????????????. user ID=" + id));
        Category category = categoryRepository.findById(fundingReq.getCategoryId())
                .orElseThrow(
                        () -> new IllegalArgumentException("?????? ??????????????? ????????????. ???????????? ID=" + fundingReq.getCategoryId()));
                
        // smart-contract openFunding ?????? ??????
        // ????????? ?????? ???????????? (Error Exception ??????)
        System.out.println("---------------------------------------");
        System.out.println("\n\n\n\n\n\n\n");
        System.out.println(user.getWallet());
        System.out.println("\n\n\n\n\n\n\n");
        System.out.println("---------------------------------------");
        Funding funding = fundingRepository.save(fundingReq.toEntity(user, category));
        ethereumUtil.openFunding(funding.getId(), funding.getGoal().intValue(), user.getWallet(), user.getNickname(), 
        		funding.getTitle(), funding.getEndDatetime().toString(), owner, password);
        return new FundingIdRes(funding.getId());
    }

    @Transactional
    public List<CategoryRes> getCategoryList() {
        List<Category> categoryList = categoryRepository.findAll();
        List<CategoryRes> categoryResList = new ArrayList<>();
        for(Category c : categoryList) {
            categoryResList.add(new CategoryRes(c));
        }
        return categoryResList;
    }

    @Transactional
    public void joinFunding(DonationReq donationReq, Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("?????? User??? ????????????. user ID=" + id));
        Funding funding = fundingRepository.findById(donationReq.getFundingId())
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????. ?????? ID=" + donationReq.getFundingId()));
        if(funding.getStartDatetime().isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("?????? ????????? ?????? ???????????? ???????????????!");
        if (funding.getEndDatetime().isBefore(LocalDateTime.now())) {

            // smart-contract closeFunding ?????? ??????
            // ????????? ?????? ???????????? (Error Exception ??????)
        	ethereumUtil.closeFunding(funding.getId(), owner, password);
            throw new IllegalArgumentException("?????? ????????? ?????? ?????????????????????!");
        }
        if(!funding.getFundingState().equals(FundingState.ACCEPTED))
            throw new IllegalArgumentException("?????? ????????? ???????????? ???????????????!");

        donationReq.getKey(); // ?????? ?????? ???

        // smart-contract doanteFunding ?????? ??????
        // ????????? ?????? ???????????? (Error Exception ??????)
        
        String tx_id = ethereumUtil.donateFunding(funding.getId(), user.getWallet(), 
        		donationReq.getAmount(), donationReq.getKey()); //???????????? ?????? ??? tx id ??????
        donationRepository.save(donationReq.toEntity(user, funding, tx_id));
    }

    @Transactional
    public Boolean checkFundingOwner(Long fundingId, Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("?????? User??? ????????????. user ID=" + id));
        Funding funding = fundingRepository.findById(fundingId)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????. ?????? ID=" + fundingId));

        return funding.getUser().getId() == user.getId();
    }

    @Transactional
    public FundingDetailRes detailFunding(Long fundingId) {
        Funding funding = fundingRepository.findById(fundingId)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????. ?????? ID=" + fundingId));
        FundingRes fundingRes;
        if(funding.getDonations().size()!=0)
            fundingRes = new FundingRes(donationRepository.findDonationByFundingId(funding.getId()));
        else
            fundingRes = new FundingRes(funding);

        return new FundingDetailRes(funding.getUser().getNickname(), fundingRes, funding.getCreatedDatetime(), funding.getFundingState(), funding.getImage(), funding.getContent());
    }

    @Transactional
    public void deleteFunding(Long fundingId, Long id) {
        if(!checkFundingOwner(fundingId, id))
            throw new IllegalArgumentException("?????? ????????? ????????????!");
        Funding funding = fundingRepository.findById(fundingId)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????. ?????? ID=" + fundingId));

        if(funding.getEndDatetime().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("????????? ????????? ????????? ??? ????????????!");

        // smart-contract abortFunding ?????? ??????
        // ????????? ?????? ???????????? (Error Exception ??????)
        ethereumUtil.abortFunding(funding.getId(), owner, password);
        fundingRepository.delete(funding);
    }

    @Transactional
    public void fundingEnd() {
        List<Funding> fundings = fundingRepository.findAllByIsEndedAndEndDatetimeBefore(false, LocalDateTime.now());
        log.info("????????? ?????? ?????? : " + fundings.size());
        for(Funding f : fundings) {
            f.endFunding();
            if(f.getDonations().size()!=0 && (double) donationRepository.findBalanceByFundingId(f.getId()).getBalance() < f.getGoal()) {
                for(Donation d: f.getDonations()){
                    donationRepository.delete(d);
                }
                f.deleteDonation();
            }
            // smart-contract closeFunding ?????? ??????
            ethereumUtil.closeFunding(f.getId(), owner, password);
            fundingRepository.save(f);
        }
    }
}
